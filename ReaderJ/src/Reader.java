import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import ru.spbstu.pipeline.*;


public class Reader implements IReader {
    private final Logger log;
    private FileInputStream fis;
    private final ReaderGrammar readerGrammar;
    private final TYPE[] types = new TYPE[3];

    private ArrayList<byte[]> buffer;
    private final ArrayList<INotifier> notifiers = new ArrayList<>();
    private boolean[] goodChunks; // Проверка на обработку соответствующей порции данных

    private static final int DEFAULT_PARAM = 0;
    private static int THREAD_SLEEP = DEFAULT_PARAM;     // Время "отдыха" потока
    private static int MAX_THREADS = DEFAULT_PARAM;
    private static int cBytes;    // Количиство считываемых байтов

    public Reader(Logger log) {
        this.log = log;
        this.readerGrammar = new ReaderGrammar(new String[]{
                Grammar.BYTE_COUNT.getStrConfig(),
                Grammar.THREAD_SLEEP.getStrConfig(),
                Grammar.MAX_THREADS.getStrConfig()});

        types[0] = TYPE.BYTE;
        types[1] = TYPE.CHAR;
        types[2] = TYPE.SHORT;
    }

    @Override
    public RC setConfig(String cfg) {
        List<Lexem> lexems;
        this.log.log(Level.INFO, "set config to Reader");
        lexems = ReaderParser.getGrammar(cfg, this.readerGrammar, this.log);
        if (lexems == null)
            return RC.CODE_CONFIG_GRAMMAR_ERROR;
        RC msg = ReaderParser.paramAnalysis(lexems, this.readerGrammar, this.log);
        if (msg != RC.CODE_SUCCESS)
            return msg;

        for (Lexem lex : lexems){
            if (lex.getToken().equals(Grammar.BYTE_COUNT.getStrConfig())){
                if (cBytes != 0) {
                    this.log.log(Level.WARNING, "ERROR: reader twice cBytes");
                    return RC.CODE_CONFIG_GRAMMAR_ERROR;
                }

                cBytes = Integer.parseInt(lex.getData());

                if (cBytes <= 0) {
                    this.log.log(Level.WARNING, "ERROR: reader cBytes <= 0");
                    return RC.CODE_CONFIG_GRAMMAR_ERROR;
                }
            }
            else if (lex.getToken().equals(Grammar.THREAD_SLEEP.getStrConfig())){
                if (THREAD_SLEEP != 0) {
                    this.log.log(Level.WARNING, "ERROR: reader twice THREAD_SLEEP");
                    return RC.CODE_CONFIG_GRAMMAR_ERROR;
                }

                THREAD_SLEEP = Integer.parseInt(lex.getData());

                if (THREAD_SLEEP <= 0) {
                    this.log.log(Level.WARNING, "ERROR: reader THREAD_SLEEP <= 0");
                    return RC.CODE_CONFIG_GRAMMAR_ERROR;
                }
            }
            else if (lex.getToken().equals(Grammar.MAX_THREADS.getStrConfig())){
                if (MAX_THREADS != 0) {
                    this.log.log(Level.WARNING, "ERROR: reader twice max_threads");
                    return RC.CODE_CONFIG_GRAMMAR_ERROR;
                }

                MAX_THREADS = Integer.parseInt(lex.getData());

                if (MAX_THREADS <= 0) {
                    this.log.log(Level.WARNING, "ERROR: reader MAX_THREADS <= 0");
                    return RC.CODE_CONFIG_GRAMMAR_ERROR;
                }

                goodChunks = new boolean[MAX_THREADS];
                Arrays.fill(goodChunks, false);
            }
        }

        buffer = new ArrayList<>(cBytes);
        for (int i = 0; i < MAX_THREADS; i++){
            buffer.add(new byte[cBytes / MAX_THREADS]);
        }

        return RC.CODE_SUCCESS;
    }

    @Override
    public RC setInputStream(FileInputStream fis) {
        this.fis = fis;

        // Проверка на соответствие: количество данных % количество потоков
        try {
            if (fis.available() < cBytes)
                cBytes = fis.available();

            if (cBytes % MAX_THREADS != 0 || (fis.available() % MAX_THREADS != 0) || (fis.available() % cBytes != 0)){
                log.log(Level.WARNING, "ERROR: reader have to many threads for data calculation! or" +
                        " avalibleData % readData != 0");
                return RC.CODE_INVALID_ARGUMENT;
            }

        }
        catch (IOException ex){
            log.log(Level.WARNING, ex.getMessage());
            return RC.CODE_INVALID_ARGUMENT;
        }

        return RC.CODE_SUCCESS;
    }

    @Override
    public RC setConsumer(IConsumer c) {
        this.notifiers.add(c.getNotifier());
        return RC.CODE_SUCCESS;
    }

    // Данную функцию вызывать у Reader`a запрещено
    @Override
    public RC setProducer(IProducer p) {
        return RC.CODE_INVALID_OUTPUT_STREAM;
    }

    @Override
    public TYPE[] getOutputTypes() {
        return types;
    }

    @Override
    public IMediator getMediator(TYPE type) {
        return new Reader.ReaderMediator(type);
    }

    // Производим оповещения
    private boolean makeNotifies(int[] chunksNotifyCount){
        boolean hasNotify = false;

        for (int i = 0; i < MAX_THREADS; i++) {
            if (!goodChunks[i]) {
                // Вызываем notify
                RC msg = notifiers.get(i).notify(i + 1, ++chunksNotifyCount[i]);

                if (msg != RC.CODE_SUCCESS) {
                    log.log(Level.WARNING, "MAX NOTIFICATION LIMIT");
                    System.exit(-1);
                }
                hasNotify = true;
            }
        }

        return hasNotify;
    }

    // Разбиваем данные для их дальнейшего распределения по работникам
    private void prepareData(byte[] tempBuffer, int size){
        byte[] temp = new byte[size];

        for (int i = 0; i < MAX_THREADS; i++){
            System.arraycopy(tempBuffer, i * size, temp, 0, size);
            buffer.add(i, temp.clone());
        }
    }

    @Override
    public void run(){
        int posEnd = 0;
        final int SIZE = cBytes / MAX_THREADS; // Размер данных на каждый поток
        byte[] tempBuffer = new byte[cBytes];

        // Массив для хранения номера оповещения, разбиваемых для потоков.
        int[] chunksNotifyCount = new int[MAX_THREADS];
        Arrays.fill(chunksNotifyCount, 0);

        try {
            int dataEnd = this.fis.available();

            while (posEnd < dataEnd) {

                if (cBytes >= dataEnd) // Последняя порция данных
                    cBytes = dataEnd - posEnd;

                log.log(Level.INFO, "Reader try to READ data");
                this.fis.read(tempBuffer, 0, cBytes);
                posEnd += cBytes;

                // Подготавливаем буфферный массив для дальнейшей обработки
                prepareData(tempBuffer, SIZE);

                // Распределяем данные по потокам и ждем, пока порция либо обработается, либо процесс завершится с ошибкой
                boolean hasNotify = false;
                do {

                    // После отправки сообщения впадаем в сон
                    if (hasNotify)
                        Thread.sleep(THREAD_SLEEP);

                    hasNotify = makeNotifies(chunksNotifyCount);

                } while (hasNotify);

                // Обновляем индексы, обнуляем булевый массив
                for (int i = 0; i < chunksNotifyCount.length; i++) {
                    chunksNotifyCount[i] = 0;
                    goodChunks[i] = false;
                }
            }

            // Рассылаем завершающий нуль по всем потокам!
            buffer = null;
            boolean endFlag = true;
            while (endFlag) {

                endFlag = makeNotifies(chunksNotifyCount);
                if (endFlag)
                    Thread.sleep(THREAD_SLEEP);
            }

        }
        catch (NullPointerException ex){
            log.log(Level.WARNING, "Reader nullptr exception");
        }
        catch (IndexOutOfBoundsException ex){
            log.log(Level.WARNING, "Reader: " + ex.getMessage());
        }
        catch (IOException ex){
            log.log(Level.WARNING, "Reader: " + ex.getMessage());
        }
        catch (InterruptedException ex){
            log.log(Level.WARNING, "ERROR: reader thread sleeping exception; " + ex.getMessage());
        }

        this.log.log(Level.INFO, "Reader working success");
    }

    class ReaderMediator implements IMediator{
        private final TYPE type;

        ReaderMediator(TYPE type){
            this.type = type;
        }

        @Override
        public Object getData(int dataId) {
            if (buffer == null) {
                goodChunks[dataId - 1] = true;
                return null;
            }

            final int SIZE = cBytes / MAX_THREADS;
            final int index = dataId - 1;

            // Отправляем кусок данных под определенным индексом, более о нем оповещать никого не нужно
            log.log(Level.INFO, "Reader try to get data: " + (dataId));
            goodChunks[index] = true;

            switch (type){
                case BYTE:
                    return buffer.get(index).clone();
                case CHAR:
                    String temp = Arrays.toString(buffer.get(index));
                    return temp.toCharArray();
                case SHORT:
                    short[] shortBuffer = new short[SIZE / 2];
                    byte[] tempBuffer = buffer.get(index);
                    for (int i = 0, k = 0; i < SIZE; i += 2, k++){
                        shortBuffer[k] = (short)((tempBuffer[i + 1] << 8) + (tempBuffer[i] & 0xff));
                    }
                    return shortBuffer;
            }

            return null;
        }
    }
}

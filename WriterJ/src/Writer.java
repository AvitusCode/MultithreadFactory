import ru.spbstu.pipeline.*;


import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Writer implements IWriter {
    private final Logger log;
    private FileOutputStream fos;
    private final WriterGrammar writerGrammar;
    private final ArrayList<Pair<IMediator, TYPE>> mediators = new ArrayList<>();
    private boolean[] goodChunk; // Готовность считать новую порцию данных
    private boolean[] goodWrite; // Готовность записать обработанные данные
    private final TYPE[] types = new TYPE[3];

    private byte[] buffer;
    private static int MAX_THREADS;
    private static int MAX_NOTIFICATION;
    private static int cBytes;
    private static boolean THREAD_ACTIVE;

    public Writer(Logger log) {
        this.log = log;
        this.writerGrammar = new WriterGrammar(new String[]{
                Grammar.BYTE_COUNT.getStrConfig(),
                Grammar.MAX_NOTIFICATIONS.getStrConfig(),
                Grammar.MAX_THREADS.getStrConfig()});
        types[0] = TYPE.BYTE;
        types[1] = TYPE.CHAR;
        types[2] = TYPE.SHORT;
    }

    @Override
    public RC setConfig(String cfg) {
        this.log.log(Level.INFO, "Read and set cfg for writer");

        final List<Lexem> lexems = WriterParser.getGrammar(cfg, writerGrammar, log);
        if (lexems == null)
            return RC.CODE_CONFIG_GRAMMAR_ERROR;
        RC msg = WriterParser.paramAnalysis(lexems, writerGrammar, log);
        if (msg != RC.CODE_SUCCESS)
            return msg;

        for (Lexem lex : lexems){
            if (lex.getToken().equals(Grammar.BYTE_COUNT.getStrConfig())){
                if (cBytes != 0)
                    return RC.CODE_CONFIG_GRAMMAR_ERROR;
                cBytes = Integer.parseInt(lex.getData());
                buffer = new byte[cBytes];
            }
            else if (lex.getToken().equals(Grammar.MAX_THREADS.getStrConfig())){
                if (MAX_THREADS != 0){
                    log.log(Level.WARNING, "Thread count has already exist");
                    return RC.CODE_CONFIG_GRAMMAR_ERROR;
                }

                MAX_THREADS = Integer.parseInt(lex.getData());
                if (MAX_THREADS <= 0){
                    log.log(Level.WARNING, "ERROR: Writer thread count <= 0!");
                    return RC.CODE_INVALID_ARGUMENT;
                }

                goodChunk = new boolean[MAX_THREADS];
                goodWrite = new boolean[MAX_THREADS];
                Arrays.fill(goodChunk, false);
                Arrays.fill(goodWrite, false);
            }
            else if (lex.getToken().equals(Grammar.MAX_NOTIFICATIONS.getStrConfig())){
                if (MAX_NOTIFICATION != 0){
                    log.log(Level.WARNING, "Thread count has already exist");
                    return RC.CODE_CONFIG_GRAMMAR_ERROR;
                }

                MAX_NOTIFICATION = Integer.parseInt(lex.getData());

                if (MAX_NOTIFICATION <= 0){
                    log.log(Level.WARNING, "ERROR: Writer thread count <= 0!");
                    return RC.CODE_INVALID_ARGUMENT;
                }
            }
        }

        if (cBytes % MAX_THREADS != 0){
            log.log(Level.WARNING,"ERROR: to many/little threads!");
            return RC.CODE_INVALID_ARGUMENT;
        }

        return RC.CODE_SUCCESS;
    }

    @Override
    public RC setOutputStream(FileOutputStream fos) {
        this.fos = fos;
        return RC.CODE_SUCCESS;
    }

    // Не должно использоваться
    @Override
    public RC setConsumer(IConsumer c) {
        log.log(Level.WARNING, "You cannot set consumer to writer!");
        return RC.CODE_FAILED_PIPELINE_CONSTRUCTION;
    }

    @Override
    public RC setProducer(IProducer p) {
        TYPE finalType = null;
        TYPE[] pTypes = p.getOutputTypes();

        for (int i = 0; i < pTypes.length; i++) {
            for (int j = 0; j < types.length; j++){

                if (types[j].equals(pTypes[i])){
                    if (pTypes[i].equals(TYPE.BYTE)){
                        finalType = TYPE.BYTE;
                    }
                    else if (pTypes[i].equals(TYPE.CHAR) && finalType != TYPE.BYTE){
                        finalType = TYPE.CHAR;
                    }
                    else if (pTypes[i].equals(TYPE.SHORT) && finalType != TYPE.BYTE){
                        finalType = TYPE.SHORT;
                    }
                }
            }
        }
        if (finalType == null){
            log.log(Level.WARNING, "No overlap by datatype with writer and his producer");
            return RC.CODE_FAILED_PIPELINE_CONSTRUCTION;
        }

        // TODO: может оптимизировать интерфейс (добавить новый метод), чтобы не использовать класс Pair?
        mediators.add(new Pair<>(p.getMediator(finalType), finalType));
        if (mediators.size() > MAX_THREADS){
            log.log(Level.WARNING, "To many mediators");
            return RC.CODE_INVALID_ARGUMENT;
        }

        return RC.CODE_SUCCESS;
    }


    @Override
    public INotifier getNotifier(){
        return new Writer.WriterNotifier();
    }

    private RC writeData(byte[] data){
        if (data == null || data.length == 0)
            return RC.CODE_SUCCESS;

        try{
            fos.write(data, 0, data.length);

        }catch (IOException e){
            log.log(Level.WARNING, "Something wrong with data writing process");
            return RC.CODE_FAILED_TO_WRITE;
        }

        return RC.CODE_SUCCESS;
    }

    // Получить данные
    private byte[] getData(int index){
        byte[] array = null;

        switch (mediators.get(index).getSecond()) {
            case SHORT:
                short[] dataS = (short[]) mediators.get(index).getFirst().getData(index + 1);
                if (dataS == null)
                    break;

                array = new byte[dataS.length * 2];

                for (int j = 0, k = 0; j < dataS.length; j += 2, k++) {
                    array[j] = (byte) (dataS[k] & 0xff);
                    array[j + 1] = (byte) ((dataS[k] >> 8) & 0xff);
                }
                break;
            case CHAR:
                char[] dataC = (char[]) mediators.get(index).getFirst().getData(index + 1);
                if (dataC == null)
                    break;
                array = new String(dataC).getBytes();
                break;
            case BYTE:
                array = (byte[]) mediators.get(index).getFirst().getData(index + 1);
        }

        return array;
    }

    // Класс осуществляет попутку получить данные от продюсеров. Если все получено, то возвращает true, иначе - false
    private boolean workingProcess(){
        boolean writeFlag = true;

        for (int i = 0; i < MAX_THREADS; i++) {
            if (!goodWrite[i]) {
                writeFlag = false;
            } else {
                continue;
            }

            // Порция еще не готова
            if (!goodChunk[i]) {
                continue;
            }

            log.log(Level.INFO, "Writer trying to confirm: " + (i + 1));
            byte[] array = getData(i);

            // Если пришел нуль, то время завершить работу
            if (array == null) {
                log.log(Level.INFO, "Writer stops!");
                THREAD_ACTIVE = false;
                return false;
            }

            int SIZE = cBytes / MAX_THREADS;
            // Кладем данные в буффер в соответствующую позицию
            System.arraycopy(array, 0, buffer, SIZE * i, SIZE);
            goodWrite[i] = true;
            goodChunk[i] = false;
        }

        return writeFlag;
    }

    @Override
    public void run(){
        THREAD_ACTIVE = true;

        while (THREAD_ACTIVE) {

            // Если нужно записать данные в файл, то выполняем цикл
            if (workingProcess()) {
                log.log(Level.INFO, "Writer try to write data");

                RC msg = writeData(buffer);

                if (msg != RC.CODE_SUCCESS) {
                    THREAD_ACTIVE = false;
                    break;
                }

                // Попробовать оптимизировать
                Arrays.fill(buffer, (byte) 0);
                Arrays.fill(goodChunk, false);
                Arrays.fill(goodWrite, false);
            }
        }
    }

    class WriterNotifier implements INotifier{

        WriterNotifier(){
        }

        @Override
        public RC notify(int idChunk, int numNotify){

            if (numNotify >= MAX_NOTIFICATION){
                log.log(Level.WARNING, "ERROR: writer max notification limit! dataId: " + idChunk);
                THREAD_ACTIVE = false;
                return RC.CODE_CHUNK_TAKEN;
            }
            else if (idChunk > MAX_THREADS){
                log.log(Level.WARNING, "too big id in writer!");
                return RC.CODE_INVALID_ARGUMENT;
            }
            else if (goodWrite[idChunk - 1]){
                log.log(Level.WARNING, "This Chunk was already written");
                THREAD_ACTIVE = false;
                return RC.CODE_CHUNK_TAKEN;
            }
            else if (!THREAD_ACTIVE){
                byte[] array = getData(idChunk - 1);

                if (array != null){
                    log.log(Level.WARNING, "ERROR: process stops, but data id not null");
                    System.exit(-1);
                }
            }

            if (numNotify == 1){
                log.log(Level.INFO, "Writer has notify data: " + (idChunk));
            }
            goodChunk[idChunk - 1] = true; // Готовы обработать соответствующую порцию данных

            return RC.CODE_SUCCESS;
        }
    }
}

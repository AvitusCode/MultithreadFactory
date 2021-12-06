import ru.spbstu.pipeline.*;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;


// Таблица подстановки
public class LookUpTable implements IExecutor {
    private final Logger log; // Логгер
    private final TYPE[] types = new TYPE[3]; // типы данных
    private TYPE finalType = null; // Тип данных, которые будем принимать у посредника

    private final LookUpTableGrammar lktgrammar; // Грамматика таблицы подстановки
    private final Table tableCore = new Table();
    private byte[] replaced; // Измененные данные

    // Посредник и notifier
    private IMediator mediator;
    private INotifier notifier;

    private static final int DEFAULT_PARAM = 0;
    private boolean CURRENT_ID_CHUNK = false;
    private boolean NEXT_ID_CHUNK = false;

    private int FINAL_ID_CHUNK = DEFAULT_PARAM;
    private int CURRENT_NOTIFY_COUNT = DEFAULT_PARAM; // Текущее количество оповещений
    private int MAX_NOTIFICATION; // Максимальное количество принимаемых оповещений
    private int THREAD_SLEEP;     // Время "отдыха" потока
    private boolean THREAD_ACTIVE;   // Флаг конца потока

    public LookUpTable(Logger log) {
        this.log = log;
        this.lktgrammar = new LookUpTableGrammar(new String[]{
                Grammar.TABLE.getStrConfig(),
                Grammar.MAX_NOTIFICATIONS.getStrConfig(),
                Grammar.THREAD_SLEEP.getStrConfig()});

        this.types[0] = TYPE.BYTE;
        this.types[1] = TYPE.SHORT;
        this.types[2] = TYPE.CHAR;
    }

    private byte[] workWithData(byte[] toReplace) {
        if (toReplace == null)
            return null;

        byte[] replacedR = new byte[toReplace.length];
        Hashtable<Byte, Byte> lkt = tableCore.getTable();

        for(int i = 0; i < toReplace.length; ++i) {
            if (lkt.containsKey(toReplace[i])) {
                replacedR[i] = lkt.get(toReplace[i]);
            } else {
                replacedR[i] = toReplace[i];
            }
        }

        return replacedR;
    }

    @Override
    public RC setConfig(String cfg) {
        this.log.log(Level.INFO, "Set LookUpTable config");

        final List<Lexem> lexems = ExecutorParser.getGrammar(cfg, lktgrammar, log);
        if (lexems == null)
            return RC.CODE_CONFIG_GRAMMAR_ERROR;
        RC msg = ExecutorParser.paramAnalysis(lexems, lktgrammar, log);
        if (msg != RC.CODE_SUCCESS)
            return msg;

        for (Lexem lex : lexems) {

            if (lex.getToken().equals(Grammar.TABLE.getStrConfig()) && tableCore.isEmpty()) {
                try {
                    FileInputStream fis = new FileInputStream(lex.getData());
                    byte[] table = new byte[fis.available()];
                    fis.read(table, 0, fis.available());
                    fis.close();
                    if (!tableCore.addTable(table)) {
                        this.log.log(Level.WARNING, "Broken bijection in lookuptable");
                        return RC.CODE_INVALID_ARGUMENT;
                    }
                } catch (FileNotFoundException e) {
                    log.log(Level.WARNING, "In worker " + e.getMessage());
                    return RC.CODE_CONFIG_SEMANTIC_ERROR;
                } catch (IndexOutOfBoundsException | NullPointerException e) {
                    log.log(Level.WARNING, "In worker " + e.getMessage());
                    return RC.CODE_CONFIG_SEMANTIC_ERROR;
                } catch (IOException e) {
                    log.log(Level.WARNING, "In worker " + e.getMessage());
                    return RC.CODE_CONFIG_SEMANTIC_ERROR;
                }
            }
            else if (lex.getToken().equals(Grammar.MAX_NOTIFICATIONS.getStrConfig())){
                Integer num = Integer.parseInt(lex.getData());

                if (num <= 0){
                    return RC.CODE_INVALID_ARGUMENT;
                }
                MAX_NOTIFICATION = num;
            }
            else if (lex.getToken().equals(Grammar.THREAD_SLEEP.getStrConfig())){
                Integer num = Integer.parseInt(lex.getData());

                if (num <= 0){
                    return RC.CODE_INVALID_ARGUMENT;
                }
                THREAD_SLEEP = num;
            }
        }

        return RC.CODE_SUCCESS;
    }

    @Override
    public RC setConsumer(IConsumer consumer) {
        notifier = consumer.getNotifier();
        return RC.CODE_SUCCESS;
    }

    @Override
    public RC setProducer(IProducer producer) {
        TYPE[] pTypes = producer.getOutputTypes();

        // Устанавливаем пересечение по подходящему типу данных
        for (int i = 0; i < pTypes.length; i++) {
            for (int j = 0; j < types.length; j++) {

                if (types[j].equals(pTypes[i])) {
                    if (pTypes[i].equals(TYPE.BYTE)) {
                        finalType = TYPE.BYTE;
                        break; // Байты - самый подходящий тип
                    } else if (pTypes[i].equals(TYPE.CHAR) && finalType != TYPE.BYTE) {
                        finalType = TYPE.CHAR;
                    } else if (pTypes[i].equals(TYPE.SHORT) && finalType != TYPE.BYTE && finalType != TYPE.CHAR) {
                        finalType = TYPE.SHORT;
                    }
                }
            }

            if (finalType.equals(TYPE.BYTE))
                break;
        }

        if (finalType == null) {
            log.log(Level.WARNING, "No overlap by data type in the LKT worker");
            return RC.CODE_FAILED_PIPELINE_CONSTRUCTION;
        }

        mediator = producer.getMediator(finalType);

        return RC.CODE_SUCCESS;
    }

    @Override
    public TYPE[] getOutputTypes() {
        return types;
    }

    @Override
    public INotifier getNotifier(){
        return new LookUpTable.LktNotifier();
    }

    @Override
    public IMediator getMediator(TYPE type) {
        // Здесь сoздается посредник для консьюмеора
        return new LookUpTable.LktMediator(type);
    }

    @Override
    public void run(){
        THREAD_ACTIVE = true;

        while (THREAD_ACTIVE)
        {
            if (NEXT_ID_CHUNK && !CURRENT_ID_CHUNK)
            {
                byte[] buffer = null;
                switch (finalType){
                    case BYTE:
                        buffer = (byte[])mediator.getData(FINAL_ID_CHUNK);
                        break;
                    case SHORT:
                        short[] dataS = (short[])mediator.getData(FINAL_ID_CHUNK);
                        if (dataS == null)
                            break;

                        buffer = new byte[dataS.length * 2];

                        for (int i = 0, k = 0; i < buffer.length; i += 2, k++){
                            buffer[i]     = (byte)(dataS[k] & 0xff);
                            buffer[i + 1] = (byte)((dataS[k] >> 8) & 0xff);
                        }
                        break;
                    case CHAR:
                        char[] dataC = (char[])mediator.getData(FINAL_ID_CHUNK);
                        if (dataC == null)
                            break;

                        buffer = new String(dataC).getBytes();
                        break;
                }

                CURRENT_ID_CHUNK = true;
                NEXT_ID_CHUNK = false;
                CURRENT_NOTIFY_COUNT = DEFAULT_PARAM;
                replaced = buffer != null ? workWithData(buffer) : null;
            }

            if (CURRENT_ID_CHUNK){
                RC msg = notifier.notify(FINAL_ID_CHUNK, ++CURRENT_NOTIFY_COUNT);

                if (msg != RC.CODE_SUCCESS){
                    log.log(Level.WARNING, "ERROR: max_notification count in worker in Thread: " + Thread.currentThread());
                    System.exit(-1);
                    return;
                }

                // После оповещения впадаем в сон
                try{
                    Thread.sleep(THREAD_SLEEP);
                }
                catch(InterruptedException ex) {
                    log.log(Level.WARNING, "Something wrong with thread sleep! " + ex.getMessage());
                    return;
                }
            }
        }

        log.log(Level.INFO, "Thread (Worker) finish work success: " + Thread.currentThread());
    }

    class LktNotifier implements INotifier {
        private boolean first = true;
        // default constructor
        LktNotifier() {
        }

        @Override
        public RC notify(int idChunk, int numNotify) {

            // Превысили лимит оповещений
            if (numNotify >= MAX_NOTIFICATION) {
                THREAD_ACTIVE = false;
                replaced = null;
                return RC.CODE_CHUNK_TAKEN;
            }

            // После установки на конвейер - каждый работник на одной линии получает даные ВСЕГДА по одному айди
            if (first){
                FINAL_ID_CHUNK = idChunk;
                first = false;
            }

            if (numNotify == 1){
                if (NEXT_ID_CHUNK){
                    log.log(Level.WARNING, "Trying to get new data while we dont get previous");
                    return RC.CODE_CHUNK_TAKEN;
                }

                NEXT_ID_CHUNK = true;
                log.log(Level.INFO, "Worker "
                        + Thread.currentThread().getName()
                        + " has notify data: " + (idChunk));

                if (idChunk != FINAL_ID_CHUNK){
                    log.log(Level.WARNING, "ERROR: Worker ID != CONST ID");
                    THREAD_ACTIVE = false;
                    replaced = null;
                    return RC.CODE_CHUNK_TAKEN;
                }
            }

            return RC.CODE_SUCCESS;
        }
    }

    class LktMediator implements IMediator{

        private final TYPE type;

        LktMediator(TYPE type){
            this.type = type;
        }

        @Override
        public Object getData(int idData) {
            // Отправляем завершающий нуль и выходим из вечного цикла
            if (replaced == null) {
                THREAD_ACTIVE = false;
                return null;
            }

            if (idData != FINAL_ID_CHUNK){
                log.log(Level.WARNING, "The data, which we are trying to get by ID, does not match the current ID");
                THREAD_ACTIVE = false;
                replaced = null;
                return null;
            }

            log.log(Level.INFO, "Worker "
                    + Thread.currentThread().getName()
                    + " try to get data: " + (idData));

            switch (type){
                case BYTE:
                    byte[] result = replaced.clone();
                    CURRENT_ID_CHUNK = false;
                    return result;
                case CHAR:
                    String temp = Arrays.toString(replaced);
                    CURRENT_ID_CHUNK = false;
                    return temp.toCharArray();
                case SHORT:
                    short[] shortBuffer = new short[replaced.length / 2];
                    for (int i = 0, k = 0; i < replaced.length; i += 2, k++){
                        shortBuffer[k] = (short)((replaced[i + 1] << 8) + (replaced[i] & 0xff));
                    }
                    CURRENT_ID_CHUNK = false;
                    return shortBuffer;
            }

            return null;
        }
    }
}

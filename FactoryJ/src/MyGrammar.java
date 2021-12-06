import ru.spbstu.pipeline.BaseGrammar;

public class MyGrammar extends BaseGrammar {
    static final String[] sa = new String[7];
    static String delimiter = ";";

    // Инициализируем имеющиеся токены грамматики
    static{
        sa[Grammar.INPUT.ordinal()] = Grammar.INPUT.getStrConfig();
        sa[Grammar.OUTPUT.ordinal()] = Grammar.OUTPUT.getStrConfig();
        sa[Grammar.READER_AND_CFG.ordinal()] = Grammar.READER_AND_CFG.getStrConfig();
        sa[Grammar.WRITER_AND_CFG.ordinal()] = Grammar.WRITER_AND_CFG.getStrConfig();
        sa[Grammar.EXECUTOR_NAME_AND_CFG.ordinal()] = Grammar.EXECUTOR_NAME_AND_CFG.getStrConfig();
        sa[Grammar.ARRAY_OF_EXECUTOR_POS.ordinal()] = Grammar.ARRAY_OF_EXECUTOR_POS.getStrConfig();
        sa[Grammar.MAX_THREADS.ordinal()] = Grammar.MAX_THREADS.getStrConfig();
    }

    public MyGrammar(){
        super(sa);
    }

    public final String delimiterGr(){
        return delimiter;
    }
}

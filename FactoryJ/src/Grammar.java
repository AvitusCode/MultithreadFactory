


public enum Grammar {
    INPUT("INPUT"),
    OUTPUT("OUTPUT"),
    READER_AND_CFG("READER_AND_CFG"),
    WRITER_AND_CFG("WRITER_AND_CFG"),
    EXECUTOR_NAME_AND_CFG("EXECUTOR_NAME_AND_CFG"),
    ARRAY_OF_EXECUTOR_POS("ARRAY_OF_EXECUTOR_POS"),
    MAX_THREADS("MAX_THREADS"),
    BYTE_COUNT("BYTE_COUNT"),
    TABLE("TABLE"),
    MAX_NOTIFICATIONS("MAX_NOTIFICATIONS"),
    THREAD_SLEEP("THREAD_SLEEP");

    private final String token;
    private Grammar(String token){this.token = token;}
    public String getStrConfig(){return this.token;}
}

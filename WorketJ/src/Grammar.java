

public enum Grammar {
    INPUT("INPUT"),
    OUTPUT("OUTPUT"),
    READER_CFG("READER_CFG"),
    WRITER_CFG("WRITER_CFG"),
    EXECUTOR_NAME_AND_CFG("EXECUTOR_NAME_AND_CFG"),
    ARRAY_OF_EXECUTOR_POS("ARRAY_OF_EXECUTOR_POS"),
    BYTE_COUNT("BYTE_COUNT"),
    CSHITF("CSHIFT"),
    SHIFT_DIRECT("SHIFT_DIRECT"),
    TABLE("TABLE"),
    MAX_NOTIFICATIONS("MAX_NOTIFICATIONS"),
    THREAD_SLEEP("THREAD_SLEEP"),
    MAX_THREADS("MAX_THREADS"),
    LEFT("LEFT"),
    RIGHT("RIGHT");

    private final String token;

    private Grammar(String token) {
        this.token = token;
    }

    public String getStrConfig() {
        return this.token;
    }
}

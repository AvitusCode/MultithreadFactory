package ru.spbstu.pipeline;

public abstract class BaseGrammar {
    private final String[] tokens;
    private static final String delimiter = "";

    protected BaseGrammar(String[] tokens) {this.tokens = tokens;}
    public final int numTokens() {return this.tokens == null ? 0 : this.tokens.length;}
    public final String token(int index){
        return this.tokens != null && index >= 0 && index < this.tokens.length ? tokens[index] : null;
    }
    public String delimiter() {return "=";}
}

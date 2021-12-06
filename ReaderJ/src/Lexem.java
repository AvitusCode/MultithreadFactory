public class Lexem {
    private final String token;
    private final String data;

    public Lexem(String token, String data){
        this.token = token;
        this.data = data;
    }

    public String getToken(){return token;}
    public String getData(){return data;}

    @Override
    public String toString() {
        return "Lexem {" + "token" + token + data + "}";
    }
}

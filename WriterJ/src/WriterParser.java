import ru.spbstu.pipeline.RC;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WriterParser {

    public static List<Lexem> getGrammar(String file, WriterGrammar writerGrammar, Logger log){
        if (file == null){
            log.log(Level.WARNING, "null file in reader config");
            return null;
        }

        List<Lexem> lexems = new ArrayList<>();
        try {
            BufferedReader bf = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            String line = null;

            while((line = bf.readLine()) != null) {
                line = line.replaceAll(" ", "");
                String[] str = line.split(writerGrammar.delimiter());
                if (str.length != 2) {
                    log.log(Level.WARNING, "Config reader error");
                    return null;
                }

                Lexem lex = new Lexem(str[0], str[1]);
                lexems.add(lex);
            }

            bf.close();
        } catch (FileNotFoundException var6) {
            log.log(Level.WARNING, "Config reader error");
            return null;
        } catch (IOException var7) {
            log.log(Level.WARNING, "Config reader error" + var7.getMessage());
            return null;
        } catch (NumberFormatException var8) {
            log.log(Level.WARNING, "Number format exception");
            return null;
        }

        return lexems;
    }

    public static RC paramAnalysis(List<Lexem> lexems, WriterGrammar writerGrammar, Logger log){
        if (lexems == null || lexems.size() != writerGrammar.numTokens() || lexems.isEmpty()){
            log.log(Level.WARNING, "null lexems or its empty or to much/low params");
            return RC.CODE_CONFIG_GRAMMAR_ERROR;
        }

        for (Lexem lex : lexems) {
            for (int i = 0; i < writerGrammar.numTokens(); i++) {
                if (lex.getToken().equals(writerGrammar.token(i)))
                    break;
                else if (!lex.getToken().equals(writerGrammar.token(i)) && i == writerGrammar.numTokens() - 1) {
                    log.log(Level.WARNING, "Error: something wrong with grammar");
                    return RC.CODE_CONFIG_GRAMMAR_ERROR;
                }
            }
        }

        return RC.CODE_SUCCESS;
    }
}

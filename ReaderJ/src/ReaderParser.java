import ru.spbstu.pipeline.RC;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReaderParser {

    public static List<Lexem> getGrammar(String file, ReaderGrammar readerGrammar, Logger log){
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
                String[] str = line.split(readerGrammar.delimiter());
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

    public static RC paramAnalysis(List<Lexem> lexems, ReaderGrammar readerGrammar, Logger log){
        if (lexems == null || lexems.isEmpty()){
            log.log(Level.WARNING, "null lexems or its empty");
            return RC.CODE_CONFIG_GRAMMAR_ERROR;
        }

        if (lexems.size() != readerGrammar.numTokens()){
            log.log(Level.WARNING, "Grammar size error in Reader");
            return RC.CODE_CONFIG_GRAMMAR_ERROR;
        }

        for (Lexem lex : lexems) {
            for (int i = 0; i < readerGrammar.numTokens(); i++) {
                if (lex.getToken().equals(readerGrammar.token(i)))
                    break;
                else if (!lex.getToken().equals(readerGrammar.token(i)) && i == readerGrammar.numTokens() - 1) {
                    log.log(Level.WARNING, "Error: something wrong with grammar");
                    return RC.CODE_CONFIG_GRAMMAR_ERROR;
                }
            }
        }

        return RC.CODE_SUCCESS;
    }
}

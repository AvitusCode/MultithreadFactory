


import ru.spbstu.pipeline.RC;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ExecutorParser {

    public static List<Lexem> getGrammar(String file, LookUpTableGrammar lkGrammar, Logger log){
        if (file == null){
            log.log(Level.WARNING, "null file in reader config");
            return null;
        }

        List<Lexem> lexems = new ArrayList<>();
        try {
            BufferedReader bf = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            String line = null;

            while((line = bf.readLine()) != null) {
                line = line.replaceAll(" ", ""); // Убираем ненужные пробельные символы
                String[] str = line.split(lkGrammar.delimiter());
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

    public static RC paramAnalysis(List<Lexem> lexems, LookUpTableGrammar lkGrammar, Logger log){
        if (lexems == null || lexems.isEmpty()){
            log.log(Level.WARNING, "null lexems or its empty");
            return RC.CODE_CONFIG_GRAMMAR_ERROR;
        }

        if (lexems.size() != lkGrammar.numTokens()){
            log.log(Level.WARNING, "lexems size != grammar tokens size");
            return RC.CODE_CONFIG_GRAMMAR_ERROR;
        }

        for (Lexem lex : lexems) {
            for (int i = 0; i < lkGrammar.numTokens(); i++) {
                if (lex.getToken().equals(lkGrammar.token(i)))
                    break;
                else if (!lex.getToken().equals(lkGrammar.token(i)) && i == lkGrammar.numTokens() - 1) {
                    log.log(Level.WARNING, "Error: something wrong with grammar");
                    return RC.CODE_CONFIG_GRAMMAR_ERROR;
                }
            }
        }

        return RC.CODE_SUCCESS;
    }
}
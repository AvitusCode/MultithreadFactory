import ru.spbstu.pipeline.RC;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ManagerParser {
    public ManagerParser() {
    }

    public static Map<String, String> getGrammar(String file, MyGrammar regulars, Logger log) {
        if (file == null) {
            log.log(Level.WARNING, "null file in reader config");
            return null;
        } else {
            Map<String, String> lexems = new HashMap<>();

            try {
                BufferedReader bf = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
                String line = null;

                while((line = bf.readLine()) != null) {
                    line = line.replaceAll(" ", "");
                    String[] str = line.split(regulars.delimiter());
                    if (str.length != 2) {
                        log.log(Level.WARNING, "Config reader error");
                        return null;
                    }

                    if (lexems.containsKey(str[0])){
                        log.log(Level.WARNING, "ERROR: this config also contains in configuration map");
                        return null;
                    }
                    lexems.put(str[0], str[1]);
                }

                bf.close();

                if (lexems.size() != regulars.numTokens()){
                    log.log(Level.WARNING, "Grammar read error. To many or to low config parametrs");
                    return null;
                }

                return lexems;
            } catch (FileNotFoundException var8) {
                log.log(Level.WARNING, "Config reader error");
                return null;
            } catch (IOException var9) {
                log.log(Level.WARNING, "Config reader error" + var9.getMessage());
                return null;
            } catch (NumberFormatException var10) {
                log.log(Level.WARNING, "Number format exception");
                return null;
            }
        }
    }

    public static RC paramAnalysis(Map<String, String> lexems, MyGrammar regulars, Logger log) {
        if (lexems != null && !lexems.isEmpty()) {

            for (Map.Entry<String, String> entry : lexems.entrySet()) {

                for (int i = 0; i < regulars.numTokens(); ++i) {
                    if (entry.getKey().equals(regulars.token(i)))
                        break;
                    else if (!entry.getKey().equals(regulars.token(i)) && i == regulars.numTokens() - 1) {
                        log.log(Level.WARNING, "Error: something wrong with grammar");
                        return RC.CODE_CONFIG_GRAMMAR_ERROR;
                    }
                }
            }

            return RC.CODE_SUCCESS;
        } else {
            log.log(Level.WARNING, "null lexems or its empty");
            return RC.CODE_CONFIG_GRAMMAR_ERROR;
        }
    }
}

import java.util.Hashtable;

// Класс, представляющий таблицу подстановки
public class Table {
    private final Hashtable<Byte, Byte> table;

    Table(){
        table = new Hashtable<>();
    }
    Table(Hashtable<Byte, Byte> table){
        this.table = table;
    }

    /*
    * @params byte[] - Массив байтов с парами значений i - заменяемый символ, i+1 - замена
    * Функция добавляет таблицу а также осуществляет проверку на биекцию
    * */
    boolean addTable(byte[] bytes){
        if (bytes.length % 2 != 0) {
            return false;
        } else {
            for(int i = 0; i < bytes.length; i += 2) {
                if (table.containsKey(bytes[i]) || table.containsKey(bytes[i + 1])) {
                    return false;
                }

                if (table.containsValue(bytes[i + 1])) {
                    return false;
                }

                table.put(bytes[i], bytes[i + 1]);
            }

            return true;
        }
    }

    boolean isEmpty(){
        return table.isEmpty();
    }

    Hashtable<Byte, Byte> getTable(){
        return table;
    }
}



import ru.spbstu.pipeline.RC;

public class Pipeline {

    public static void main(String[] args){

        if (args.length != 1) {
            System.out.println("CMD arguments error!");
            System.exit(-1);
        }

        Manager manager = new Manager(args[0]);
        RC code = manager.run();

        if (code == RC.CODE_SUCCESS)
            System.out.println("Good Work, operation success!");
        else
            System.out.println("Pipeline error off");
    }
}
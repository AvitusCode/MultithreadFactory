package ru.spbstu.pipeline;

import java.io.FileOutputStream;

public interface IWriter extends IPipelineStep, IConsumer, Runnable{
    RC setOutputStream(FileOutputStream var1);
}

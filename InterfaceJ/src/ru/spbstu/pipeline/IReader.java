package ru.spbstu.pipeline;

import java.io.FileInputStream;

public interface IReader extends IPipelineStep, IProducer, Runnable {
    RC setInputStream(FileInputStream var1);
}

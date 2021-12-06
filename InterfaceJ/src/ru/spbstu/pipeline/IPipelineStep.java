package ru.spbstu.pipeline;

public interface IPipelineStep extends IConfigurable {
    RC setConsumer(IConsumer var1);
    RC setProducer(IProducer var1);
}

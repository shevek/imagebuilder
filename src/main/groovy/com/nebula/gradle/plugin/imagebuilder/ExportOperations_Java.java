package com.nebula.gradle.plugin.imagebuilder;

import org.gradle.api.Action;

// @Category(ImageTask)
public class ExportOperations_Java implements Operations {

    private final ImageTask task;

    public ExportOperations_Java(ImageTask task) {
        this.task = task;
    }

    public void export(Action<ExportSystem> c) {
        final ExportSystem e = new ExportSystem();
        c.execute(e);
        /*
        task.customize(new Action<ImageTask.Context>() {
            @Override
            public void execute(ImageTask.Context t) {
                try {
                    _export(t, e);
                } catch (IOException ex) {
                    throw Throwables.propagate(ex);
                } catch (LibGuestFSException ex) {
                    throw Throwables.propagate(ex);
                }
            }
        });
        */
    }
}

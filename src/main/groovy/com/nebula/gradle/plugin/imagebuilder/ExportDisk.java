/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nebula.gradle.plugin.imagebuilder;

import groovy.lang.Closure;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.anarres.qemu.image.QEmuImageFormat;
import org.gradle.api.Action;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.api.tasks.Input;

/**
 *
 * @author shevek
 */
public class ExportDisk {

    @Input
    public String outputFileName = "image";
    @Input
    public QEmuImageFormat outputFormat = QEmuImageFormat.raw;
    @Input
    public List<ExportDiskPartition> partitions = new ArrayList<ExportDiskPartition>();

    public void outputFile(@Nonnull String name) {
        outputFileName = name;
    }

    public void outputFile(@Nonnull String name, @Nonnull QEmuImageFormat format) {
        outputFile(name);
        outputFormat(format);
    }

    public void outputFile(@Nonnull String name, @Nonnull String format) {
        outputFile(name);
        outputFormat(format);
    }

    public void outputFormat(@Nonnull QEmuImageFormat format) {
        outputFormat = format;
    }

    public void outputFormat(@Nonnull String format) {
        outputFormat(QEmuImageFormat.valueOf(format));
    }

    public void partition(@Nonnull String name, @Nonnull Action<ExportDiskPartition> closure) {
        ExportDiskPartition p = new ExportDiskPartition(name, partitions.size() + 1);
        closure.execute(p);
        partitions.add(p);
    }

    public void partition(@Nonnull String name, @Nonnull Closure<?> closure) {
        partition(name, new ClosureBackedAction<ExportDiskPartition>(closure));
    }

    // Allow an extra 64K for leading and trailing metadata.
    public long getSize() {
        long size = 0;
        for (ExportDiskPartition partition : partitions)
            size += ExportUtils.round(partition.size);
        return size + 64 * ExportDiskPartition.K;
    }

}

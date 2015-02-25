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
import org.gradle.api.Action;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.api.tasks.Input;

/**
 *
 * @author shevek
 */
public class ExportSystem {

    @Input
    public List<ExportDisk> disks = new ArrayList<ExportDisk>();

    public void disk(@Nonnull Action<ExportDisk> closure) {
        ExportDisk p = new ExportDisk();
        closure.execute(p);
        disks.add(p);
    }

    public void disk(@Nonnull Closure<?> closure) {
        disk(new ClosureBackedAction<ExportDisk>(closure));
    }
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nebula.gradle.plugin.imagebuilder;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

/**
 *
 * @author shevek
 */
public class ExportDiskPartition {

    public static final long K = 1024;
    public static final long M = 1024 * K;
    public static final long G = 1024 * M;
    public static final long T = 1024 * G;
    public final String name;

    // Not to be set by user
	/* pp */ final int index;
    public String filesystem = "ext4";
    public String mountpoint;
    public String options = "errors=remount-ro";
    public long size = 0;
    public boolean bootable = false;

    /* pp */ ExportDiskPartition(@Nonnull String name, @Nonnegative int index) {
        this.name = name;
        this.index = index;
    }

}

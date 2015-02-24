package com.nebula.gradle.plugin.imagebuilder;

import java.io.File;
import org.gradle.api.Action;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecSpec;

public class TmpfsTask extends AbstractImageTask {

    public TmpfsTask() {
        setDescription("Mounts a tmpfs to make imagebuilder faster.");
        upToDateWhenFalse();
    }

    @TaskAction
    // @Override
    public void run() {
        final File tmpDir = getTmpDir();

        tmpDir.mkdirs();
        String stat = qx("stat", "-f", "-c", "%T", tmpDir.getAbsolutePath());
        if (!"tmpfs".equals(stat)) {
            String uid = qx("id", "-u");
            String gid = qx("id", "-g");
            getProject().exec(new Action<ExecSpec>() {
                @Override
                public void execute(ExecSpec t) {
                    t.commandLine(
                            "sudo", "mount",
                            "-t", "tmpfs",
                            "-o", "size=6G,mode=0755,noatime,uid=$uid,gid=$gid",
                            "tmpfs", tmpDir.getAbsolutePath()
                    );
                }
            });
        }
    }
}

package com.nebula.gradle.plugin.imagebuilder;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.redhat.et.libguestfs.GuestFS;
import com.redhat.et.libguestfs.LibGuestFSException;
import groovy.lang.Closure;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import org.anarres.qemu.image.QEmuImage;
import org.anarres.qemu.image.QEmuImageFormat;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.process.ExecSpec;

public abstract class AbstractImageTask extends ConventionTask {

    @Input
    File tmpDir;
    @Input
    File outputDir;
    @Input
    String imageFileName = "image";
    @Input
    String imageFileExtension = ".tmp";
    @Input
    QEmuImageFormat imageFormat = QEmuImageFormat.raw;
    // @OutputFile
    File imageFile;

    public AbstractImageTask() {
        conventionMapping("tmpDir", new Callable<File>() {
            @Override
            public File call() throws Exception {
                return new File(getProject().getBuildDir(), "tmp");
            }
        });
        conventionMapping("outputDir", new Callable<File>() {
            @Override
            public File call() throws Exception {
                return new File(getProject().getBuildDir(), "output");
            }
        });
        conventionMapping("imageFile", new Callable<File>() {
            @Override
            public File call() throws Exception {
                String name = getImageFileName() + getImageFileExtension();
                return new File(getTmpDir(), name);
            }
        });
    }

    protected void upToDateWhenFalse() {
        getOutputs().upToDateWhen(new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task t) {
                return false;
            }
        });
    }

    @Nonnull
    public File getTmpDir() {
        return tmpDir;
    }

    @Nonnull
    public File getOutputDir() {
        return outputDir;
    }

    @Nonnull
    public String getImageFileName() {
        return imageFileName;
    }

    @Nonnull
    public String getImageFileExtension() {
        return imageFileExtension;
    }

    public QEmuImageFormat getImageFormat() {
        return imageFormat;
    }

    public void imageFormat(@Nonnull QEmuImageFormat format) {
        imageFormat = format;
    }

    public void imageFormat(@Nonnull String format) {
        imageFormat(QEmuImageFormat.valueOf(format));
    }

    public File getImageFile() {
        return imageFile;
    }

    public void imageFile(@Nonnull Object file) {
        imageFile = getProject().file(file);
    }

    // Must be public, called via owner.qx()
    @Nonnull
    public String qx(@Nonnull final List<String> command) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        getProject().exec(new Action<ExecSpec>() {
            @Override
            public void execute(ExecSpec t) {
                t.commandLine(command);
                t.setStandardOutput(out);
            }
        });
        return out.toString().trim();
    }

    @Nonnull
    public String qx(@Nonnull String... command) {
        return qx(Arrays.asList(command));
    }

    public void fsCreate(@Nonnull File imageFile, @Nonnull QEmuImageFormat imageFormat, @Nonnegative long size) throws IOException {
        File parentFile = imageFile.getParentFile();
        if (parentFile != null)
            parentFile.mkdirs();
        QEmuImage image = new QEmuImage(imageFile);
        image.create(imageFormat, size);
    }

    public void fsCreate(@Nonnull File imageFile, @Nonnull String imageFormat, @Nonnegative long size) throws IOException {
        fsCreate(imageFile, QEmuImageFormat.valueOf(imageFormat), size);
    }

    @Nonnull
    public GuestFS fsOpen(@Nonnull File imageFile, @Nonnull QEmuImageFormat imageFormat, @CheckForNull Action<GuestFS> c)
            throws LibGuestFSException {
        GuestFS g = new GuestFS();
        g.set_trace(true);
        Map<String, Object> optargs = ImmutableMap.<String, Object>of(
                "format", imageFormat.name(),
                // TODO: Support readonly for exports.
                "readonly", Boolean.FALSE
        );
        g.add_drive_opts(imageFile.getAbsolutePath(), optargs);
        if (c != null) {
            c.execute(g);
        }
        g.launch();
        return g;
    }

    @Nonnull
    public GuestFS fsOpen(@Nonnull File imageFile, @Nonnull String imageFormat, @CheckForNull Action<GuestFS> c)
            throws LibGuestFSException {
        return fsOpen(imageFile, QEmuImageFormat.valueOf(imageFormat), c);
    }

    @Nonnull
    public GuestFS fsOpen(@Nonnull File imageFile, @Nonnull String imageFormat, @CheckForNull final Closure<?> c)
            throws LibGuestFSException {
        return fsOpen(imageFile, QEmuImageFormat.valueOf(imageFormat), c == null ? null : new Action<GuestFS>() {
            @Override
            public void execute(GuestFS t) {
                c.call(t);
            }
        });
    }

    @Nonnull
    public GuestFS fsOpen(@Nonnull File imageFile, @Nonnull QEmuImageFormat imageFormat)
            throws LibGuestFSException {
        return fsOpen(imageFile, imageFormat, (Action<GuestFS>) null);
    }

    public GuestFS fsOpen(@Nonnull File imageFile, @Nonnull String imageFormat)
            throws LibGuestFSException {
        return fsOpen(imageFile, imageFormat, (Action<GuestFS>) null);
    }

    @Nonnull
    public GuestFS fsOpen(Action<GuestFS> c) throws LibGuestFSException {
        return fsOpen(getImageFile(), getImageFormat(), c);
    }

    public GuestFS fsOpen() throws LibGuestFSException {
        return fsOpen(null);
    }

    @Nonnull
    public String fsDevice(@Nonnull GuestFS g,
            @Nonnegative int deviceCount,
            @Nonnegative int deviceIndex)
            throws LibGuestFSException {
        String[] devices = g.list_devices();
        // println "Devices are " + devices
        return devices[deviceIndex];
    }

    public void fsInspect(@Nonnull final GuestFS g)
            throws LibGuestFSException {
        Map<String, String> filesystems = g.list_filesystems();
        switch (filesystems.size()) {
            case 0:
                throw new LibGuestFSException("No filesystems found");
            case 1:
                for (Map.Entry<String, String> e : filesystems.entrySet())
                    g.mount(e.getKey(), "/");
                return;
        }

        // default:
        String[] osRoots = g.inspect_os();
        getLogger().debug("Operating system roots are " + Arrays.toString(osRoots));
        // osRoots.find { String osRoot ->
        for (String osRoot : osRoots) {
            if (!"linux".equals(g.inspect_get_type(osRoot)))
                continue;
            Map<String, String> mountpoints = g.inspect_get_mountpoints(osRoot);
            getLogger().debug("Operating system mountpoints are " + mountpoints); // Sort by mount order!
            mountpoints = new TreeMap<String, String>(mountpoints);
            for (Map.Entry<String, String> e : mountpoints.entrySet())
                g.mount(e.getKey(), e.getValue());
        }
    }

    @Nonnull
    private File fsMkfifo() {
        final File fifoFile = new File(getTmpDir(), getName() + ".fifo");
        if (fifoFile.exists())
            fifoFile.delete();
        getProject().exec(new Action<ExecSpec>() {
            @Override
            public void execute(ExecSpec t) {
                t.commandLine("mkfifo", fifoFile.getAbsolutePath());
            }
        });
        return fifoFile;
    }

    private void add(@Nonnull List<String> out, @Nonnull String... in) {
        out.addAll(Arrays.asList(in));
    }

    public void fsUpload(@Nonnull GuestFS g,
            @Nonnull File srcRoot, @CheckForNull File srcState,
            @Nonnull String dstRoot)
            throws LibGuestFSException {
        g.mkdir_p(dstRoot);

        File tarFile = fsMkfifo();

        // Needs to read files only readable as root.
        final List<String> fakerootCommand = new ArrayList<String>();
        final List<String> tarCommand = new ArrayList<String>();
        add(tarCommand, "tar", "-cvf", tarFile.getAbsolutePath());

        if (srcState != null)
            add(fakerootCommand, "fakeroot", "-s", srcState.getAbsolutePath());
        else
            add(tarCommand, "--owner", "root", "--group", "root");

        add(tarCommand, "-C", srcRoot.getAbsolutePath(), ".");

        new Thread("fs-upload-tar") {
            @Override
            public void run() {
                getProject().exec(new Action<ExecSpec>() {
                    @Override
                    public void execute(ExecSpec t) {
                        t.commandLine(Iterables.concat(fakerootCommand, tarCommand));
                    }
                });
            }
        }.start();

        g.tar_in(tarFile.getAbsolutePath(), dstRoot);

        getProject().delete(tarFile);
    }

    public void fsUpload(@Nonnull GuestFS g,
            @Nonnull File srcRoot, @CheckForNull File srcState)
            throws LibGuestFSException {
        fsUpload(g, srcRoot, srcState, "/");
    }

    public void fsDownload(@Nonnull GuestFS g) {
        assert false : "Not yet implemented.";
    }

    public void fsClose(@Nonnull GuestFS g)
            throws LibGuestFSException {
        g.sync();
        g.shutdown();
        g.close();
    }
}

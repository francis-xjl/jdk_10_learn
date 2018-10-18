/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package jdk.jfr.internal;

import java.io.IOException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import jdk.jfr.RecordingState;

/**
 * Class responsible for dumping recordings on exit
 *
 */
final class ShutdownHook implements Runnable {
    private final PlatformRecorder recorder;
    Object tlabDummyObject;

    ShutdownHook(PlatformRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public void run() {
        // this allocation is done in order to fetch a new TLAB before
        // starting any "real" operations. In low memory situations,
        // we would like to take an OOM as early as possible.
        tlabDummyObject = new Object();

        for (PlatformRecording recording : recorder.getRecordings()) {
            if (recording.getDumpOnExit() && recording.getState() == RecordingState.RUNNING) {
                dump(recording);
            }
        }
        recorder.destroy();
    }

    private void dump(PlatformRecording recording) {
        try {
            WriteableUserPath dest = recording.getDestination();
            if (dest == null) {
                dest = makeDumpOnExitPath(recording);
                recording.setDestination(dest);
            }
            if (dest != null) {
                recording.stop("Dump on exit");
            }
        } catch (Exception e) {
            Logger.log(LogTag.JFR, LogLevel.DEBUG, () -> "Could not dump recording " + recording.getName() + " on exit.");
        }
    }

    private WriteableUserPath makeDumpOnExitPath(PlatformRecording recording) {
        try {
            String name = Utils.makeFilename(recording.getRecording());
            AccessControlContext acc = recording.getNoDestinationDumpOnExitAccessControlContext();
            return AccessController.doPrivileged(new PrivilegedExceptionAction<WriteableUserPath>() {
                @Override
                public WriteableUserPath run() throws Exception {
                    return new WriteableUserPath(recording.getDumpOnExitDirectory().toPath().resolve(name));
                }
            }, acc);
        } catch (PrivilegedActionException e) {
            Throwable t = e.getCause();
            if (t instanceof SecurityException) {
                Logger.log(LogTag.JFR, LogLevel.WARN, "Not allowed to create dump path for recording " + recording.getId() + " on exit.");
            }
            if (t instanceof IOException) {
                Logger.log(LogTag.JFR, LogLevel.WARN, "Could not dump " + recording.getId() + " on exit.");
            }
            return null;
        }
    }

    static final class ExceptionHandler implements Thread.UncaughtExceptionHandler {
        public void uncaughtException(Thread t, Throwable e) {
            JVM.getJVM().uncaughtException(t, e);
        }
    }
}

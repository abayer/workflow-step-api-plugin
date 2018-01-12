/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.steps;

import com.google.common.collect.Sets;
import hudson.AbortException;
import hudson.Functions;
import hudson.model.Executor;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.CauseOfInterruption;
import jenkins.model.InterruptedBuildAction;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;

/**
 * Special exception that can be thrown out of {@link StepContext#onFailure} to indicate that the flow was aborted from the inside.
 * (This could be caught like any other exception and rethrown or ignored. It only takes effect if thrown all the way up.)
 * <p>No stack trace is printed (except by {@link #getCause} and/or {@link #getSuppressed} if present),
 * and you can control the {@link Result} and {@link CauseOfInterruption}.
 * <p>Analogous to {@link Executor#interrupt(Result, CauseOfInterruption...)} but does not assume we are running inside an executor thread.
 * <p>There is no need to call this from {@link StepExecution#stop} since in that case the execution owner
 * should have set a {@link jenkins.model.CauseOfInterruption.UserInterruption} and {@link Result#ABORTED}.
 */
public final class FlowInterruptedException extends InterruptedException {

    private final @Nonnull Result result;
    private final @Nonnull List<CauseOfInterruption> causes;

    /**
     * Creates a new exception.
     * @param result the desired result for the flow, typically {@link Result#ABORTED}
     * @param causes any indications
     */
    public FlowInterruptedException(@Nonnull Result result, @Nonnull CauseOfInterruption... causes) {
        this.result = result;
        this.causes = Arrays.asList(causes);
    }

    @Whitelisted
    public @Nonnull Result getResult() {
        return result;
    }

    @Whitelisted
    public @Nonnull List<CauseOfInterruption> getCauses() {
        return causes;
    }

    /**
     * If a build catches this exception, it should use this method to report it.
     * @param run
     * @param listener
     */
    public void handle(Run<?,?> run, TaskListener listener) {
        Set<CauseOfInterruption> boundCauses = new HashSet<>();
        for (InterruptedBuildAction a : run.getActions(InterruptedBuildAction.class)) {
            boundCauses.addAll(a.getCauses());
        }
        Collection<CauseOfInterruption> diff = Sets.difference(new LinkedHashSet<>(causes), boundCauses);
        if (!diff.isEmpty()) {
            run.addAction(new InterruptedBuildAction(diff));
            for (CauseOfInterruption cause : diff) {
                cause.print(listener);
            }
        }
        print(getCause(), listener);
        for (Throwable t : getSuppressed()) {
            print(t, listener);
        }
    }
    private static void print(@CheckForNull Throwable t, @Nonnull TaskListener listener) {
        if (t instanceof AbortException) {
            listener.getLogger().println(t.getMessage());
        } else if (t != null) {
            listener.getLogger().println(Functions.printThrowable(t).trim()); // TODO 2.43+ use Functions.printStackTrace
        }
    }

}

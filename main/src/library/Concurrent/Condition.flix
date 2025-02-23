/*
 * Copyright 2021 Jonathan Lindegaard Starup
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

namespace Concurrent/Condition {

    ///
    /// A wrapper around a Java Condition.
    ///
    @Internal
    pub enum Condition(##java.util.concurrent.locks.Condition)

    ///
    /// An enum reflecting the two exceptions occurring in condition methods, lock state errors and thread interruptions.
    ///
    @Internal
    pub enum ConditionError {
        case MonitorStateError(##java.lang.IllegalMonitorStateException)
        case InterruptedError(##java.lang.InterruptedException)
    }

    ///
    /// Causes the current thread to wait until it is signalled or interrupted.
    ///
    @Internal
    pub def await(condition: Condition): Result[Unit, ConditionError] \ IO =
        import java.util.concurrent.locks.Condition.await(): Unit \ IO;
        let Condition(c) = condition;
        try {
            Ok(await(c))
        } catch {
            case e: ##java.lang.InterruptedException => Err(InterruptedError(e))
            case e: ##java.lang.IllegalMonitorStateException => Err(MonitorStateError(e))
        }

    ///
    /// Causes the current thread to wait until it is signalled or interrupted, or the specified waiting
    /// time(nanos) elapses. The method returns an estimate of the number of nanoseconds remaining to wait
    /// given the supplied nanosTimeout value upon return, or a value less than or equal to zero if it timed out.
    ///
    @Internal
    pub def awaitTimeout(condition: Condition, timeout: Int64): Result[Int64, ConditionError] \ IO =
        import java.util.concurrent.locks.Condition.awaitNanos(Int64): Int64 \ IO;
        let Condition(c) = condition;
        try {
            Ok(awaitNanos(c, timeout))
        } catch {
            case e: ##java.lang.InterruptedException => Err(InterruptedError(e))
            case e: ##java.lang.IllegalMonitorStateException => Err(MonitorStateError(e))
        }

    ///
    /// Causes the current thread to wait until it is signalled.
    ///
    @Internal
    pub def awaitUninterruptibly(condition: Condition): Result[Unit, ##java.lang.IllegalMonitorStateException] \ IO =
        import java.util.concurrent.locks.Condition.awaitUninterruptibly(): Unit \ IO;
        let Condition(c) = condition;
        try {
            Ok(awaitUninterruptibly(c))
        } catch {
            case e: ##java.lang.IllegalMonitorStateException => Err(e)
        }

    ///
    /// Wakes up one waiting thread.
    ///
    @Internal
    pub def signal(condition: Condition): Result[Unit, ##java.lang.IllegalMonitorStateException] \ IO =
        import java.util.concurrent.locks.Condition.signal(): Unit \ IO;
        let Condition(c) = condition;
        try {
            Ok(signal(c))
        } catch {
            case e: ##java.lang.IllegalMonitorStateException => Err(e)
        }

    ///
    /// Wakes up all waiting threads.
    ///
    @Internal
    pub def signalAll(condition: Condition): Result[Unit, ##java.lang.IllegalMonitorStateException] \ IO =
        import java.util.concurrent.locks.Condition.signalAll(): Unit \ IO;
        let Condition(c) = condition;
        try {
            Ok(signalAll(c))
        } catch {
            case e: ##java.lang.IllegalMonitorStateException => Err(e)
        }

}

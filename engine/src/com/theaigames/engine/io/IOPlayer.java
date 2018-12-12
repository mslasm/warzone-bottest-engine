// Copyright 2015 theaigames.com (developers@theaigames.com)

//    Licensed under the Apache License, Version 2.0 (the "License");
//    you may not use this file except in compliance with the License.
//    You may obtain a copy of the License at

//        http://www.apache.org/licenses/LICENSE-2.0

//    Unless required by applicable law or agreed to in writing, software
//    distributed under the License is distributed on an "AS IS" BASIS,
//    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//    See the License for the specific language governing permissions and
//    limitations under the License.
//
//    For the full copyright and license information, please view the LICENSE
//    file that was distributed with this source code.

package com.theaigames.engine.io;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * IOPlayer class
 *
 * Does the communication between the bot process and the engine
 *
 * @author Jackie Xu <jackie@starapple.nl>, Jim van Eeden <jim@starapple.nl>
 */
public class IOPlayer implements Runnable {

    private String streamName;
    private Process process;
    private OutputStreamWriter outputStream;
    private InputStreamGobbler processStdOutGobbler;
    private InputStreamGobbler processStdErrGobbler;

    private Logger communicationLogger;
    private List<String> errorLog;
    private List<String> communicationLog;

    private int errorCounter;
    private boolean finished;
    private final int maxErrors = 2;

    // the last unprocessed line received form the bot. Is replaced by a new line whenever a
    // new line is recieved. Is also reset to null every time the data is consumed
    private String response;

    public IOPlayer(Process process, String streamName, Logger communicationLogger) {
        this.streamName = streamName;
        this.process = process;
        this.communicationLogger = communicationLogger;

        // send data to the process stdin
        this.outputStream = new OutputStreamWriter(process.getOutputStream());

        // get responses from the process's stdout
        this.processStdOutGobbler = new InputStreamGobbler(process.getInputStream(), this::recordInputFromProcess);

        // record data from the process's stderr, but ignore it
        this.processStdErrGobbler = new InputStreamGobbler(process.getErrorStream(), null);

        this.communicationLog = new LinkedList<>();
        this.errorLog = new LinkedList<>();
        this.errorCounter = 0;

        this.finished = false;
    }

    public synchronized void recordInputFromProcess(String data) {
        this.response = data;
    }

    // processes a line by reading it or writing it
    public void sendToPlayer(String line) throws IOException {
        if (!this.finished) {
            logCommunication("->", line);
            try {
                this.outputStream.write(line + "\n");
                this.outputStream.flush();
            } catch(IOException e) {
                this.errorLog.add("Writing to bot failed");
                logCommunication("!!", "Writing to bot failed");
            }
        }
    }

    // waits for a response from the bot
    public String getResponse(long timeOut) {
        if (this.errorCounter > this.maxErrors) {
            logCommunication("<-", "<skipping player - too many errors>");
            return "";
        }

        long timeStart = System.currentTimeMillis();

        while(this.response == null) {
            long timeNow = System.currentTimeMillis();
            long timeElapsed = timeNow - timeStart;

            if(timeOut > 0 && timeElapsed >= timeOut) {
                logCommunication("<-", "<timeout>");
                errorLog.add("Response timed out after " + timeOut + "ms (return 'No moves' instead of nothing or play faster)");

                this.errorCounter++;
                if (this.errorCounter > this.maxErrors) {
                    errorLog.add("Maximum number (" + this.maxErrors + ") of time-outs reached: skipping all moves.");
                    finish();
                }
                return "";
            }

            try { Thread.sleep(2); } catch (InterruptedException e) {}
        }

        String lastResponse = this.response;
        this.response = null;

        if(lastResponse.equalsIgnoreCase("No moves")) {
            logCommunication("<-",  "<no moves>");
            return "";
        }
        logCommunication("<-", lastResponse);
        return lastResponse;
    }

    // ends the bot process and it's communication
    public void finish() {

        if(this.finished)
            return;

    	try {
            this.outputStream.close();
        } catch (IOException e) {}

        this.process.destroy();
        try {
            this.process.waitFor();
        } catch (InterruptedException ex) {
            Logger.getLogger(IOPlayer.class.getName()).log(Level.SEVERE, null, ex);
        }

        this.finished = true;
    }

    public Process getProcess() {
        return this.process;
    }

    public String getStdout() {
        return this.processStdOutGobbler.getData();
    }

    public String getStderr() {
        return this.processStdErrGobbler.getData();
    }

    public List<String> getErrorLog() {
        return this.errorLog;
    }

    public List<String> getCommunicationLog() {
        return this.communicationLog;
    }

    private void logCommunication(String type, String message) {
        String logMessage = String.format("%s [%s] '%s'\n", type, this.streamName, message);
        communicationLog.add(logMessage);
        if (communicationLogger != null) {
            communicationLogger.info(logMessage);
        }
    }

    @Override
    // start communication with the bot
    public void run() {
        this.processStdOutGobbler.start();
        this.processStdErrGobbler.start();
    }
}

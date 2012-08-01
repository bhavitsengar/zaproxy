/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2010 psiinon@gmail.com
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
package org.zaproxy.zap.extension.brk;

import java.awt.EventQueue;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.parosproxy.paros.view.View;
import org.zaproxy.zap.extension.httppanel.Message;


class BreakpointMessageHandler {

    private static final Logger logger = Logger.getLogger(BreakpointMessageHandler.class);
    
    private static final java.lang.Object semaphore = new java.lang.Object();
    
    private final BreakPanel breakPanel;
    
    private List<BreakpointMessageInterface> enabledBreakpoints;
    
    public BreakpointMessageHandler(BreakPanel aBreakPanel) {
        this.breakPanel = aBreakPanel;
    }
    
    public void setEnabledBreakpoints(List<BreakpointMessageInterface> breakpoints) {
        this.enabledBreakpoints = breakpoints;
    }
    
    public boolean handleMessageReceivedFromClient(Message aMessage, boolean onlyIfInScope) {
        if ( ! isBreakpoint(aMessage, true, onlyIfInScope)) {
            return true;
        }
        // Do this outside of the semaphore loop so that the 'continue' button can apply to all queued break points
        // but be reset when the next break point is hit
        breakPanel.breakpointHit();

        synchronized(semaphore) {
            if (breakPanel.isHoldMessage()) {
                setBreakDisplay(aMessage, true);
                waitUntilContinue(true);
            }
        }
        clearAndDisableRequest();
        return ! breakPanel.isToBeDropped();
    }
    
    public boolean handleMessageReceivedFromServer(Message aMessage, boolean onlyIfInScope) {
        if (! isBreakpoint(aMessage, false, onlyIfInScope)) {
            return true;
        }
        
        // Do this outside of the semaphore loop so that the 'continue' button can apply to all queued break points
        // but be reset when the next break point is hit
        breakPanel.breakpointHit();

        synchronized(semaphore) {
            //breakPanel.breakpointHit();
            if (breakPanel.isHoldMessage()) {
                setBreakDisplay(aMessage, false);
                waitUntilContinue(false);
            }
        }
        clearAndDisableResponse();

        return ! breakPanel.isToBeDropped();
    }
    
    private void setBreakDisplay(final Message msg, boolean isRequest) {
        setHttpDisplay(breakPanel, msg, isRequest);
        breakPanel.breakpointDisplayed();
        try {
            EventQueue.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    View.getSingleton().getMainFrame().toFront();
                }
            });
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
        }
    }
    
    private void setHttpDisplay(final BreakPanel breakPanel, final Message msg, final boolean isRequest) {
        try {
            EventQueue.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    breakPanel.setMessage(msg, isRequest);
                }
            });
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
        }
        
    }
    
    private void waitUntilContinue(final boolean isRequest) {
        // Note that multiple requests and responses can get built up, so pressing continue only
        // releases the current break, not all of them.
        //breakPanel.setContinue(false);
        while (breakPanel.isHoldMessage()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                logger.warn(e.getMessage(), e);
            }
        }
        try {
            EventQueue.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    breakPanel.saveMessage(isRequest);
                }
            });
        } catch (Exception ie) {
            logger.warn(ie.getMessage(), ie);
        }
    }

    public boolean isBreakpoint(Message aMessage, boolean request, boolean onlyIfInScope) {
    	if (onlyIfInScope && ! aMessage.isInScope()) {
    		return false;
    	}
        if (request && breakPanel.isBreakRequest()) {
            // Break on all requests
            return true;
        } else if ( ! request && breakPanel.isBreakResponse()) {
            // Break on all responses
            return true;
        } else if (breakPanel.isStepping()) {
            // Stopping through all requests and responses
            return true;
        }
        
        if (enabledBreakpoints.isEmpty()) {
            // No break points
            return false;
        }
        
        // match against the break points
        synchronized (enabledBreakpoints) {
            Iterator<BreakpointMessageInterface> it = enabledBreakpoints.iterator();
            
            while(it.hasNext()) {
                BreakpointMessageInterface breakpoint = it.next();
                
                if (breakpoint.match(aMessage, onlyIfInScope)) {
                    return true;
                }
            }
        }

        return false;
    }
    
    private void clearAndDisableRequest() {
        if (EventQueue.isDispatchThread()) {
            breakPanel.clearAndDisableRequest();
        } else {
            try {
                EventQueue.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        breakPanel.clearAndDisableRequest();
                    }
                });
            } catch (Exception e) {
                logger.warn(e.getMessage(), e);
            }
        }
    }
    
    private void clearAndDisableResponse() {
        if (EventQueue.isDispatchThread()) {
            breakPanel.clearAndDisableResponse();
        } else {
            try {
                EventQueue.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        breakPanel.clearAndDisableResponse();
                    }
                });
            } catch (Exception e) {
                logger.warn(e.getMessage(), e);
            }
        }
    }
}

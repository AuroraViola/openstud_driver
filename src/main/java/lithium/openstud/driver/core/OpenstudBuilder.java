package lithium.openstud.driver.core;

import lithium.openstud.driver.exceptions.OpenstudInvalidCredentialsException;

import java.util.logging.Logger;

public class OpenstudBuilder {
    private int retryCounter = 3;
    private String webEndpoint = "https://www.studenti.uniroma1.it/phxdroidws";
    private String timetableEndpoint = "https://gomp.sapienzaapps.it/";
    private String newsEndpoint = "https://news-infostud.apps.os.sapienzaapps.it/";
    private int connectTimeout = 10;
    private int writeTimeout = 10;
    private int readTimeout = 30;
    private String studentID;
    private String password;
    private Logger logger;
    private boolean readyState = false;
    private OpenstudHelper.Mode mode = OpenstudHelper.Mode.MOBILE;
    private int limitSearchResults = 13;
    private int classroomWaitRequest = 200;

    public void setLimitSearchResults(int limitSearchResults) {
        this.limitSearchResults = limitSearchResults;
    }

    public void setClassroomWaitRequest(int millis) {
        if (millis<0) return;
        this.classroomWaitRequest = millis;
    }

    public OpenstudBuilder setRetryCounter(int retryCounter) {
        this.retryCounter = retryCounter;
        return this;
    }

    public OpenstudBuilder setWebEndpoint(String webEndpoint) {
        this.webEndpoint = webEndpoint;
        return this;
    }

    public OpenstudBuilder setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    public OpenstudBuilder setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
        return this;
    }

    public OpenstudBuilder setWriteTimeout(int writeTimeout) {
        this.writeTimeout = writeTimeout;
        return this;
    }

    public OpenstudBuilder setStudentID(String id) {
        this.studentID = id;
        return this;
    }

    public OpenstudBuilder setPassword(String password) {
        this.password = password;
        return this;
    }

    public OpenstudBuilder setLogger(Logger logger) {
        this.logger = logger;
        return this;
    }

    public OpenstudBuilder forceReadyState() {
        this.readyState = true;
        return this;
    }

    public OpenstudBuilder setMode(OpenstudHelper.Mode mode) {
        this.mode = mode;
        return this;
    }

    public void setTimetableEndpoint(String timetableEndpoint) {
        this.timetableEndpoint = timetableEndpoint;
    }
    public void setNewsEndpoint(String newsEndpoint) {
        this.newsEndpoint = newsEndpoint;
    }
    public Openstud build() {
        if (mode == OpenstudHelper.Mode.MOBILE) webEndpoint = "https://www.studenti.uniroma1.it/phxdroidws";
        else if (mode == OpenstudHelper.Mode.WEB) webEndpoint = "https://www.studenti.uniroma1.it/phoenixws";
        return new Openstud(mode, webEndpoint, timetableEndpoint, newsEndpoint, studentID, password, logger, retryCounter, connectTimeout, readTimeout, writeTimeout, readyState, classroomWaitRequest, limitSearchResults);
    }
}

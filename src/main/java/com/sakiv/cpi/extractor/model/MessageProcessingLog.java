package com.sakiv.cpi.extractor.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MessageProcessingLog {

    @JsonProperty("MessageGuid")
    private String messageGuid;

    @JsonProperty("IntegrationFlowName")
    private String integrationFlowName;

    @JsonProperty("Status")
    private String status;

    @JsonProperty("LogStart")
    private String logStart;

    @JsonProperty("LogEnd")
    private String logEnd;

    @JsonProperty("Sender")
    private String sender;

    @JsonProperty("Receiver")
    private String receiver;

    @JsonProperty("ApplicationMessageId")
    private String applicationMessageId;

    @JsonProperty("CorrelationId")
    private String correlationId;

    @JsonProperty("LogLevel")
    private String logLevel;

    public String getMessageGuid() { return messageGuid; }
    public void setMessageGuid(String messageGuid) { this.messageGuid = messageGuid; }

    public String getIntegrationFlowName() { return integrationFlowName; }
    public void setIntegrationFlowName(String integrationFlowName) { this.integrationFlowName = integrationFlowName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getLogStart() { return logStart; }
    public void setLogStart(String logStart) { this.logStart = logStart; }

    public String getLogEnd() { return logEnd; }
    public void setLogEnd(String logEnd) { this.logEnd = logEnd; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getReceiver() { return receiver; }
    public void setReceiver(String receiver) { this.receiver = receiver; }

    public String getApplicationMessageId() { return applicationMessageId; }
    public void setApplicationMessageId(String applicationMessageId) { this.applicationMessageId = applicationMessageId; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getLogLevel() { return logLevel; }
    public void setLogLevel(String logLevel) { this.logLevel = logLevel; }
}

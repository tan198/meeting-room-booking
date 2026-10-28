package com.tanle.meeting_room_booking.common.exception;

import lombok.Getter;

import java.util.Map;

@Getter
public class BusinessException extends RuntimeException{

    private final ErrorCode errorCode;
    private final Map<String,Object> details;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.details = null;
    }

    public BusinessException(ErrorCode errorCode, Map<String, Object> details) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.details = details;
    }

    public BusinessException(ErrorCode errorCode, String CustomMessage) {
        super(CustomMessage);
        this.errorCode = errorCode;
        this.details = null;
    }

    public BusinessException(ErrorCode errorCode, String CustomMessage, Map<String,Object> details) {
        super(CustomMessage);
        this.errorCode = errorCode;
        this.details = details;
    }
}

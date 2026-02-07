package com.example.midnightmusic.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A generic wrapper class that represents the result of an operation.
 * Can be either Success with data, Error with exception, or Loading state.
 * 
 * @param <T> The type of data held by this result
 */
public class Result<T> {

    @Nullable
    private final T data;

    @Nullable
    private final Exception error;

    private final Status status;

    public enum Status {
        SUCCESS,
        ERROR,
        LOADING
    }

    private Result(@Nullable T data, @Nullable Exception error, Status status) {
        this.data = data;
        this.error = error;
        this.status = status;
    }

    /**
     * Creates a successful result with data
     */
    public static <T> Result<T> success(@NonNull T data) {
        return new Result<>(data, null, Status.SUCCESS);
    }

    /**
     * Creates an error result with an exception
     */
    public static <T> Result<T> error(@NonNull Exception error) {
        return new Result<>(null, error, Status.ERROR);
    }

    /**
     * Creates an error result with a message
     */
    public static <T> Result<T> error(@NonNull String message) {
        return new Result<>(null, new Exception(message), Status.ERROR);
    }

    /**
     * Creates a loading result
     */
    public static <T> Result<T> loading() {
        return new Result<>(null, null, Status.LOADING);
    }

    /**
     * Creates a loading result with previous data
     */
    public static <T> Result<T> loading(@Nullable T data) {
        return new Result<>(data, null, Status.LOADING);
    }

    @Nullable
    public T getData() {
        return data;
    }

    @Nullable
    public Exception getError() {
        return error;
    }

    @Nullable
    public String getErrorMessage() {
        return error != null ? error.getMessage() : null;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isError() {
        return status == Status.ERROR;
    }

    public boolean isLoading() {
        return status == Status.LOADING;
    }

    @Override
    public String toString() {
        return "Result{" +
                "status=" + status +
                ", data=" + data +
                ", error=" + error +
                '}';
    }
}

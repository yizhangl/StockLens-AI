package com.stocklens.research.ai;

public class InvalidAiResponseException extends RuntimeException {
    public InvalidAiResponseException() { super("The AI response could not be validated."); }
}

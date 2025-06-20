package com.nitin.dto;

public class QueryRequest {

    private String query;
    private String message;

    public QueryRequest() {}
    public QueryRequest(String query){
        this.query = query;
    }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public String getMessage() {return message; }
    public void setMessage(String message) { this.message = message; }
}

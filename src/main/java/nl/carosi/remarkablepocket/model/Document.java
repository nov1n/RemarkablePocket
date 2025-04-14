package nl.carosi.remarkablepocket.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Document(@JsonProperty("CurrentPage") int currentPage, @JsonProperty("Name") String name) {
}

package com.intellisoft.findams.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class FileParseSummaryDto {
    String status;
    String imported;
    String updated;
    String deleted;
    String ignored;
}

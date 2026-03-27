package com.demo.search.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * Elasticsearch document representing a searchable task.
 * Indexed by search-service when task lifecycle events arrive on the {@code task-events} topic.
 */
@Document(indexName = "tasks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Keyword)
    private String projectId;

    @Field(type = FieldType.Text)
    private String projectName;

    @Field(type = FieldType.Keyword)
    private String phaseId;

    @Field(type = FieldType.Text)
    private String phaseName;

    @Field(type = FieldType.Keyword)
    private String assignedUserId;

    @Field(type = FieldType.Text)
    private String assignedUserName;
}

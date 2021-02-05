
/*
 * Copyright © 2018-2019 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.cdap.plugin.format.avro;

import com.google.common.collect.Maps;
import io.cdap.cdap.api.data.format.StructuredRecord;
import io.cdap.plugin.common.RecordConverter;
import io.cdap.plugin.format.avro.output.GenericRecordWrapper;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Creates GenericRecords from StructuredRecords without knowing the schema beforehand.
 */
public class UnstructuredToAvroTransformer extends RecordConverter<StructuredRecord, GenericRecordWrapper> {

  private final Map<Integer, Schema> schemaCache;

  public UnstructuredToAvroTransformer() {
    this.schemaCache = Maps.newHashMap();
  }

  public GenericRecordWrapper transform(StructuredRecord structuredRecord) throws IOException {
    return transform(structuredRecord, null);
  }

  @Override
  public GenericRecordWrapper transform(StructuredRecord structuredRecord,
                                        io.cdap.cdap.api.data.schema.Schema schema) throws IOException {
    io.cdap.cdap.api.data.schema.Schema structuredRecordSchema = structuredRecord.getSchema();

    Schema avroSchema = getAvroSchema(structuredRecordSchema);

    GenericRecordBuilder recordBuilder = new GenericRecordBuilder(avroSchema);
    for (Schema.Field field : avroSchema.getFields()) {
      String fieldName = field.name();
      io.cdap.cdap.api.data.schema.Schema.Field schemaField = structuredRecordSchema.getField(fieldName);
      if (schemaField == null) {
        throw new IllegalArgumentException("Input record does not contain the " + fieldName + " field.");
      }
      recordBuilder.set(fieldName, convertField(structuredRecord.get(fieldName), schemaField));
    }
    return new GenericRecordWrapper(recordBuilder.build(), avroSchema, structuredRecordSchema.hashCode());
  }

  @Override
  protected Object convertBytes(Object field) {
    if (field instanceof ByteBuffer) {
      return field;
    }
    return ByteBuffer.wrap((byte[]) field);
  }

  private Schema getAvroSchema(io.cdap.cdap.api.data.schema.Schema cdapSchema) {
    int hashCode = cdapSchema.hashCode();
    if (schemaCache.containsKey(hashCode)) {
      return schemaCache.get(hashCode);
    } else {
      Schema avroSchema = new Schema.Parser().parse(cdapSchema.toString());
      schemaCache.put(hashCode, avroSchema);
      return avroSchema;
    }
  }
}

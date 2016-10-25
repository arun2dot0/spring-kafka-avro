package avro.schema;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;

import kafka.serializer.Decoder;

public class AvroSerializer {

	 public <T> T deserialize(final byte[] bytes, final DatumReader<T> reader) throws IOException {
         final org.apache.avro.io.Decoder decoder =  DecoderFactory.get().binaryDecoder(bytes, null);
         return reader.read(null, decoder);
     }

     public <T> byte[] serialize(final T input, final DatumWriter<T> writer) throws IOException {
         final ByteArrayOutputStream stream = new ByteArrayOutputStream();

         final Encoder encoder = EncoderFactory.get().binaryEncoder(stream, null);
         writer.write(input, encoder);
         encoder.flush();

         return stream.toByteArray();
     }
 }


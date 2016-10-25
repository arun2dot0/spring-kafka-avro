package sample_kafka_javadsl.kafka.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;





import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.I0Itec.zkclient.ZkClient;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.kafka.inbound.KafkaMessageDrivenChannelAdapter;
import org.springframework.integration.kafka.outbound.KafkaProducerMessageHandler;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.config.ContainerProperties;
import org.springframework.kafka.support.KafkaNull;
import org.springframework.kafka.support.TopicPartitionInitialOffset;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.support.GenericMessage;

import avro.schema.EmployeeSchema;
import kafka.admin.AdminUtils;
import kafka.common.TopicExistsException;
import kafka.utils.ZKStringSerializer$;
import kafka.utils.ZkUtils;


@SpringBootApplication
public class Application {

	@Value("${kafka.topic}")
	private String topic;

	@Value("${kafka.messageKey}")
	private String messageKey;

	@Value("${kafka.broker.address}")
	private String brokerAddress;

	@Value("${kafka.zookeeper.connect}")
	private String zookeeperConnect;

	public static void main(String[] args) throws Exception {
		ConfigurableApplicationContext context
				= new SpringApplicationBuilder(Application.class)
				.web(false)
				.run(args);
		MessageChannel toKafka = context.getBean("toKafka", MessageChannel.class);
		GenericRecord avroRecord = new GenericData.Record(EmployeeSchema.SCHEMA$);
	
		for (int i = 0; i < 10; i++) {


			toKafka.send(new GenericMessage<> (serializeEmployee(EmployeeSchema.newBuilder()
				.setId(i)
				.setFirstName("Arun "+i)
				.setLastName("Selvamani "+i)
				.setTitle("Engineer "+i)
				.setCreatedDate("")
				.setModifiedDate("")
				.build())));
		}
		
		PollableChannel fromKafka = context.getBean("received", PollableChannel.class);
		Message<?> received = fromKafka.receive(10000);
	
		DatumReader<EmployeeSchema> reader = new SpecificDatumReader(EmployeeSchema.getClassSchema());
	
		
		while (received != null && !received.equals(KafkaNull.INSTANCE)) {
			System.out.println(received);
			received = fromKafka.receive(10000);
			
			GenericMessage<byte[]> byteMessage = 
					(GenericMessage<byte[]> )received;
			System.out.println(deserialize(reader,byteMessage.getPayload())) ;
		}
		context.close();
		System.exit(0);
	}
	
	private static byte[] serializeEmployee(EmployeeSchema record) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
		DatumWriter<EmployeeSchema> writer = new SpecificDatumWriter(EmployeeSchema.getClassSchema());
		writer.write(record, encoder);
		encoder.flush();
		out.close();
		return out.toByteArray();
	}
	  
	
	private static EmployeeSchema deserialize(DatumReader<EmployeeSchema> reader,byte[] bytes) throws IOException
	{
		BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(bytes, null);
		return reader.read(null, decoder);
	}
	@Bean
	public DatumWriter getDatumWriter(){
		return  new SpecificDatumWriter<EmployeeSchema>(EmployeeSchema.class);
	}

	@ServiceActivator(inputChannel = "toKafka")
	@Bean
	public MessageHandler handler() throws Exception {
		KafkaProducerMessageHandler<String, String> handler =
				new KafkaProducerMessageHandler<>(kafkaTemplate());
		handler.setTopicExpression(new LiteralExpression(this.topic));
		handler.setMessageKeyExpression(new LiteralExpression(this.messageKey));
		return handler;
	}

	@Bean
	public KafkaTemplate<String, String> kafkaTemplate() {
		return new KafkaTemplate<>(producerFactory());
	}

	@Bean
	public ProducerFactory<String, String> producerFactory() {

		Map<String, Object> props = new HashMap<>();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, this.brokerAddress);
		props.put(ProducerConfig.RETRIES_CONFIG, 0);
		props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
		props.put(ProducerConfig.LINGER_MS_CONFIG, 1);
		props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
		props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);

		return new DefaultKafkaProducerFactory<>(props);
	}

	@Bean
	public KafkaMessageListenerContainer<String, String> container() throws Exception {
		return new KafkaMessageListenerContainer<>(consumerFactory(),
				new ContainerProperties(new TopicPartitionInitialOffset(this.topic, 0)));
	}

	@Bean
	public ConsumerFactory<String, String> consumerFactory() {
		Map<String, Object> props = new HashMap<>();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, this.brokerAddress);
		props.put(ConsumerConfig.GROUP_ID_CONFIG, "siTestGroup");
		props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
		props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, 100);
		props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 15000);
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
		return new DefaultKafkaConsumerFactory<>(props);
	}

	@Bean
	public KafkaMessageDrivenChannelAdapter<String, String>
				adapter(KafkaMessageListenerContainer<String, String> container) {
		KafkaMessageDrivenChannelAdapter<String, String> kafkaMessageDrivenChannelAdapter =
				new KafkaMessageDrivenChannelAdapter<>(container);
		kafkaMessageDrivenChannelAdapter.setOutputChannel(received());
		return kafkaMessageDrivenChannelAdapter;
	}

	@Bean
	public PollableChannel received() {
		return new QueueChannel();
	}

	@Bean
	public TopicCreator topicCreator() {
		return new TopicCreator(this.topic, this.zookeeperConnect);
	}

	public static class TopicCreator implements SmartLifecycle {

		private final String topic;

		private final String zkConnect;

		private volatile boolean running;

		public TopicCreator(String topic, String zkConnect) {
			this.topic = topic;
			this.zkConnect = zkConnect;
		}

		@Override
		public void start() {
			ZkUtils zkUtils = new ZkUtils(new ZkClient(this.zkConnect, 6000, 6000,
				ZKStringSerializer$.MODULE$), null, false);
			try {
				AdminUtils.createTopic(zkUtils, topic, 1, 1, new Properties(), null);
			}
			catch (TopicExistsException e) {
				// no-op
			}
			this.running = true;
		}

		@Override
		public void stop() {
		}

		@Override
		public boolean isRunning() {
			return this.running;
		}

		@Override
		public int getPhase() {
			return Integer.MIN_VALUE;
		}

		@Override
		public boolean isAutoStartup() {
			return true;
		}

		@Override
		public void stop(Runnable callback) {
			callback.run();
		}

	}

}
# vertx-config-aws-ssm
A [Vert.X (v3) Config Store](http://vertx.io/docs/vertx-config/java/) backed by AWS SSM.

Built on top of the [vertx-config project](https://github.com/vert-x3/vertx-config/).

### AWS Configuration Store

The AWS Configuration Store is an extension to the Vert.x Configuration Retriever to retrieve configuration from the [AWS EC2 SSM Parameter Store](https://aws.amazon.com/ec2/systems-manager/parameter-store/).

### Using the AWS Configuration Store

To use the AWS Configuration, add the following dependency to the *dependencies* section of your build descriptor
**NOTE:** (Artifact has not yet been published):

Maven (in your `pom.xml`):
```xml
<dependency>
  <groupId>com.finovertech</groupId>
  <artifactId>vertx-config-aws-ssm</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
<dependency>
  <groupId>io.vertx</groupId>
  <artifactId>vertx-config</artifactId>
  <version>3.4.2</version>
</dependency>
```
Gradle (in your `build.gradle` file):
```gradle
compile 'io.vertx:vertx-config:3.4.2'
compile 'com.finovetech:vertx-config-aws-ssm:0.1.0-SNAPSHOT'
```

### Configuring the store

Once added to your classpath or dependencies, you need to configure the `ConfigRetriever` to use this store.
```java
ConfigStoreOptions aws = new ConfigStoreOptions()
    .setType("aws-ssm")
    .setConfig(new JsonObject()
        .put("path", "/yourBasePath")
        .put("recursive", false)
        .put("decrypt", true)

ConfigRetriever retriever = ConfigRetriever.create(vertx,
    new ConfigRetrieverOptions().addStore(aws));
```
The configuration requires:

* the `path` within the AWS parameter store to use as the base path.

You can also configure whether to get parameters from the store recursively (`recursive` boolean, `true` by default), and whether to decrypt encrypted values (`decrypt` boolean, `true` by default).

You will also need to configure your default AWS credentials and region. That can be done in your `pom.xml` file or your `build.gradle` file, or before you create the `ConfigRetriever` in your code.

### How does it work

If the `path` exists within your AWS SSM parameter store, the configuration store will read all of the values under that path by calling the AWS API with your default credentials and region.

If the configuration store cannot find the `path` or cannot connect to AWS, the configuration retrieval fails.

Periodically, AWS is polled to check if the configuration has changed.
# AWS::MemoryDB::Cluster

Congratulations on starting development! Next steps:

1. Write the JSON schema describing your resource, `aws-memorydb-cluster.json`
2. Implement your resource handlers.

The RPDK will automatically generate the correct resource model from the schema whenever the project is built via Maven. You can also do this manually with the following command: `cfn generate`.

> Please don't modify files under `target/generated-sources/rpdk`, as they will be automatically overwritten.

The code uses [Lombok](https://projectlombok.org/), and [you may have to install IDE integrations](https://projectlombok.org/setup/overview) to enable auto-complete for Lombok-annotated classes.

## Local Testing using SAM

> 1. Populate the credentials in sam-tests/\*/\*.json files
>
> 2. [setup SAM cli](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-install.html)
>
> 3. start the lambda on docker locally
>
>    ```sam local start-lambda```
> 4. test handlers
>
>   ```sam local invoke TestEntrypoint --event sam-tests/create.json --region <region> ```

## Running Contract Tests (Locally)

You can execute the following commands to run the tests.
You will need to have docker installed and running.


### Package the code with Maven
```mvn package```
### Start the code as a lambda function in the background
```sam local start-lambda &```
###Execute a specific contract test
```cfn test -- -k contract_create_delete```

###Execute all contract tests
```cfn test --enforce-timeout 10000```

####Check handler logs for failing contract tests
```rpdk.log```

## Running Contract Tests during resource registration

###Submit the resource to CloudFormation and Execute all contract tests
```cfn submit  --set-default --role-arn <cfn-logging-and-metrics-role-arn> --region <region-where-the-service-is-available>```

###Check handler logs for failing contract tests
```s3 -> CloudFormation/ContractTestResults/AWS::Service::ResourceName/<generated-name>.zip```

Please download the zip file to see the output.

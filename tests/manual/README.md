# 'Manual' tests
Each test has a profile to trigger it. There may be more that needs doing for each test,
so check the README for each test to see what is needed.

Ideally, each 'manual' test will be runnable on CI. Add instructions for setting up secrets and
whatever else is needed to a 'CI Setup' section in the test README, and modify the
[.github/workflows/wildfly-cloud-tests.yml](.github/workflows/wildfly-cloud-tests.yml) workflow
file to run the test.

| Test                                  | Profile           | Env vars                           |
|---------------------------------------|-------------------|------------------------------------|
| [rhosak](rhosak/README.md)            | -Pmanual-rhosak   | KAFKA_HOST                         |
|                                       |                   | RHOAS_CLIENT_ID                    |                         
|                                       |                   | RHOAS_CLIENT_SECRET                |

<!-- 
    TODO add your manual tests to the above table and make sure to:
    - List any env vars needed
    - elaborate on the env vars in the 'CI Setup' section of its README.md
-->

# CI and Environment variables
To trigger the tests on CI, we use environment variables since (at least so far)
these tests require additional configuration. We need a way to pass those in to 
the tests, and can do that via the `workflow_dispatch` event handled by GitHub 
Actions. 

This is available for you to run in your own repository by going to the
GitHub Actions pane and selecting 'Run Workflow'. This in turn allows you to 
pass in some input parameters to the test.

If you wish to use another image than the current latest WildFly runtime image,
you can pass this in via the `Name/tag of the base runtime image` input
parameter in the prompt that appears.

There is a maximum limit of 10 input parameters for these kind of jobs. To get
around that, the other parameter handles the full list of mentioned enviroment
variables, and is titled `Env vars (one per line) encoded with base64`.

To populate this parameter, create a file, and add the env vars there:
```shell
$ cat my.env
VARIABLE_NAME_1=value-one
VARIABLE_NAME_2=value-two
VARIABLE_NAME_3=value-three
```
Then run the following command:
```shell
$ base64 my.env
```
and use the resulting string to populate the mentioned input parameter.


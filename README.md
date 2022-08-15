# java-sdk-contrib

Java SDK contrib

To publish:
`mvn --no-transfer-progress --batch-mode --settings release/m2-settings.xml verify deploy -Dversion.modifier='-$GITHUB_SHA-SNAPSHOT'`

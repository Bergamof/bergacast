package fr.bergadev.bergacast.storage;

import fr.bergadev.bergacast.config.BergacastProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** Client of the S3-compatible object storage, configured explicitly: no AWS profile file or environment is read. */
@Configuration(proxyBeanMethods = false)
class StorageConfiguration {

    @Bean(destroyMethod = "close")
    S3Client s3Client(BergacastProperties properties) {
        BergacastProperties.Storage storage = properties.storage();
        return S3Client.builder()
                .endpointOverride(storage.endpoint())
                .region(Region.of(storage.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(storage.accessKey(), storage.secretKey())))
                // Path-style URLs (endpoint/bucket/key) work with any S3-compatible storage, whatever the bucket name.
                .forcePathStyle(true)
                // Only the checksums the S3 API requires: S3-compatible storages do not all support the newer ones.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClient(UrlConnectionHttpClient.create())
                .build();
    }
}

package com.vzaar

import spock.lang.Unroll

class CreateSignatureIntegrationSpec extends BaseIntegrationSpec {
    private static final long EXACTLY_1MB = 1024l * 1024l;
    private static final long EXACTLY_5MB = EXACTLY_1MB * 5l;
    private static final long EXACTLY_5GB = EXACTLY_5MB * 1024l;
    private static final long NOT_QUITE_5GB = EXACTLY_5GB - 1;
    private static final long NOT_QUITE_5MB = EXACTLY_5MB - 1;
    private static final long EXACTLY_16MB = 16l * 1024l * 1024l;

    def "I can create a single part signature with all the request fields"() {
        when:
        Signature signature = vzaar.videos().customUploader.signature()
                .withUploadType(UploadType.single)
                .withFilename("my_video.mp4")
                .withUploader("vzaar-java-sdk 1.0")
                .withFilesize(NOT_QUITE_5GB)
                .result()

        then:
        signature.type == UploadType.single
        signature.filename == 'my_video.mp4'
        signature.filesize == NOT_QUITE_5GB
        signature.uploader == 'vzaar-java-sdk 1.0'
        signature.accessKeyId ==~ /[A-Z0-9]{20,}/
        signature.key ==~ /vzaar\/.+\/\$\{filename\}/
        signature.acl == 'private'
        signature.policy ==~ /[A-z0-9+]{128,}=?$/
        signature.signature ==~ /[A-z0-9+\/]{24,}=?$/
        signature.successActionStatus == '201'
        signature.contentType == 'binary/octet-stream'
        signature.guid ==~ /[A-z0-9_-]{8,}/
        signature.bucket ==~ /vzaar-upload.*/
        signature.uploadHostname ==~ /https:\/\/vzaar-upload.*\.s3\.amazonaws\.com/
        signature.partSize == null
        signature.partSizeInBytes == 0
        signature.parts == 0
    }

    def "I get an error if I try to create single part signature for a file of 5GiB or over"() {
        when:
        vzaar.videos().customUploader.signature()
                .withUploadType(UploadType.single)
                .withFilename("my_video")
                .withUploader("vzaar-java-sdk 1.0")
                .withFilesize(EXACTLY_5GB)
                .result()

        then:
        VzaarServerException e = thrown(VzaarServerException)
        e.statusCode == 422
        e.statusMessage == 'Unprocessable Entity'
        e.errors.size() == 1
        e.errors.get(0).message == 'Invalid parameters'
        e.errors.get(0).detail == 'Filesize cannot exceed 5GB'
    }

    def "I can create a multipart signature with all the request fields"() {
        when:
        Signature signature = vzaar.videos().customUploader.signature()
                .withUploadType(UploadType.multipart)
                .withFilename("my_video")
                .withUploader("vzaar-java-sdk 1.0")
                .withFilesize(EXACTLY_5GB)
                .result()

        then:
        signature.accessKeyId ==~ /[A-Z0-9]{20,}/
        signature.key ==~ /vzaar\/.+\/\$\{filename\}/
        signature.acl == 'private'
        signature.policy ==~ /[A-z0-9+]{128,}=?$/
        signature.signature ==~ /[A-z0-9+\/]{24,}=?$/
        signature.successActionStatus == '201'
        signature.contentType == 'binary/octet-stream'
        signature.guid ==~ /[A-z0-9_-]{8,}/
        signature.bucket ==~ /vzaar-upload.*/
        signature.uploadHostname ==~ /https:\/\/vzaar-upload.*\.s3\.amazonaws\.com/
        signature.partSize == '16MB'
        signature.partSizeInBytes == EXACTLY_16MB
        signature.parts == (int) (EXACTLY_5GB / EXACTLY_16MB)
    }

    def "I get an error if I try to create multipart signature for a file smaller than 5GiB"() {
        when:
        vzaar.videos().customUploader.signature()
                .withUploadType(UploadType.multipart)
                .withFilename("my_video")
                .withUploader("vzaar-java-sdk 1.0")
                .withFilesize(NOT_QUITE_5MB)
                .result()

        then:
        VzaarServerException e = thrown(VzaarServerException)
        e.statusCode == 422
        e.statusMessage == 'Unprocessable Entity'
        e.errors.size() == 1
        e.errors.get(0).message == 'Invalid parameters'
        e.errors.get(0).detail == 'Filesize must be between 5MB and 5TB'
    }

    @Unroll("I can submit various desired chunk sizes - using #partSizeRequest")
    def "I can submit various desired chunk sizes"() {
        when:
        Signature signature = vzaar.videos().customUploader.signature()
                .withUploadType(UploadType.multipart)
                .withFilename("my_video")
                .withUploader("vzaar-java-sdk 1.0")
                .withFilesize(EXACTLY_5GB)
                .withDesiredPartSizeInMb(partSizeRequest)
                .result()

        then:
        signature.partSize == partSizeRequest + "MB"
        signature.partSizeInBytes == partSizeInBytes
        signature.parts == parts

        where:
        partSizeRequest | partSizeInBytes     | parts
        10              | 10l * EXACTLY_1MB   | 512
        100             | 100l * EXACTLY_1MB  | 52
        1024            | EXACTLY_1MB * 1024L | 5
        1706            | 1706l * EXACTLY_1MB | 4
        1800            | 1887436800l         | 3
        5000            | 5242880000l         | 2
        5120            | 5368709120l         | 1
    }

}

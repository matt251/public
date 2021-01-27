/*
  Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
  Permission is hereby granted, free of charge, to any person obtaining a copy of this
  software and associated documentation files (the "Software"), to deal in the Software
  without restriction, including without limitation the rights to use, copy, modify,
  merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
  permit persons to whom the Software is furnished to do so.
  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
  INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
  PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
  HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
  OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
  SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

console.log('called');

'use strict'

const AWS = require('aws-sdk');
const s3 = new AWS.S3();
const S3_BUCKET = 'photobucketmatthewtcom';
const URL_EXPIRATION_SECONDS = 300;

//Main Lambda entry point

exports.issuesigned = async (event) => {
    return await getUploadURL(event);
}


const getUploadURL = async function(event) {
  
  const randomID = parseInt(Math.random() * 10000000);
  const Key = `inbound/${randomID}.jpg`;
  const s3Params = { Bucket: S3_BUCKET, Key, Expires: URL_EXPIRATION_SECONDS, ContentType: 'image/jpeg' };

  console.log('Params: ', s3Params);

  const uploadURL = await s3.getSignedUrlPromise('putObject', s3Params);

  return JSON.stringify({uploadURL: uploadURL,Key});
}

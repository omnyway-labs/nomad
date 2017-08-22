#!/bin/sh

git clone https://${READ_BOT_TOKEN}@github.com/omnypay/omnypay-ci.git  ~/omnypay-ci --depth 1 --quiet

~/omnypay-ci/deps.sh

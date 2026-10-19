#!/usr/bin/env python3
"""Negative API examples. Uses isolated demo accounts and waits for the configured rate interval."""
import base64
import hashlib
import hmac
import json
import os
import time
import uuid
from demo import request, account, product_body, promo_body, items


def main():
    user, buyer = account('USER')
    _, seller = account('SELLER')
    _, other = account('USER')
    product = request('POST', '/products', product_body(), seller, 201)['id']
    bad = product_body(); bad['name'] = ''
    request('POST', '/products', bad, seller, 400, 'VALIDATION_ERROR')
    request('GET', '/products/' + str(uuid.uuid4()), token=buyer, expected=404, error='PRODUCT_NOT_FOUND')
    inactive = request('POST', '/products', product_body(status='INACTIVE'), seller, 201)['id']
    request('POST', '/orders', items(inactive, 1), buyer, 409, 'PRODUCT_INACTIVE')
    request('POST', '/orders', items(product, 999), buyer, 409, 'INSUFFICIENT_STOCK')
    invalid = items(product, 1); invalid['promo_code'] = 'NONE_' + uuid.uuid4().hex[:6].upper()
    request('POST', '/orders', invalid, buyer, 422, 'PROMO_CODE_INVALID')
    code = 'MIN_' + uuid.uuid4().hex[:8].upper()
    request('POST', '/promo-codes', promo_body(code, minimum=1000), seller, 201)
    invalid['promo_code'] = code
    request('POST', '/orders', invalid, buyer, 422, 'PROMO_CODE_MIN_AMOUNT')
    order = request('POST', '/orders', items(product, 1), buyer, 201)['id']
    request('GET', '/orders/' + order, token=other, expected=403, error='ORDER_OWNERSHIP_VIOLATION')
    request('POST', '/orders', items(product, 1), buyer, 429, 'ORDER_LIMIT_EXCEEDED')
    # No database changes are needed to demonstrate ordering: wait for the CREATE_ORDER limit.
    interval = int(os.environ.get('ORDER_RATE_LIMIT_MINUTES', '1')) * 60 + 1
    print(f'Waiting {interval}s to demonstrate ORDER_HAS_ACTIVE after the rate limit expires...', flush=True)
    time.sleep(interval)
    request('POST', '/orders', items(product, 1), buyer, 409, 'ORDER_HAS_ACTIVE')
    request('POST', '/orders/' + order + '/cancel', token=buyer)
    request('POST', '/orders/' + order + '/cancel', token=buyer, expected=409, error='INVALID_STATE_TRANSITION')
    request('GET', '/products', expected=401, error='TOKEN_INVALID')
    request('GET', '/products', token='broken', expected=401, error='TOKEN_INVALID')
    request('POST', '/products', product_body(), buyer, 403, 'ACCESS_DENIED')
    request('POST', '/auth/refresh', {'refresh_token': 'broken'}, expected=401, error='REFRESH_TOKEN_INVALID')
    # Construct only an expired demonstration JWT using the configured local signing key.
    secret = os.environ.get('JWT_SECRET', 'local-demo-signing-key-change-before-real-use-123456789')
    encode = lambda value: base64.urlsafe_b64encode(value).rstrip(b'=')
    header = encode(json.dumps({'alg': 'HS256', 'typ': 'JWT'}).encode())
    payload = encode(json.dumps({'iss': 'marketplace', 'sub': user['id'], 'role': 'USER', 'type': 'access', 'exp': int(time.time()) - 60}).encode())
    signed = header + b'.' + payload
    expired = signed + b'.' + encode(hmac.new(secret.encode(), signed, hashlib.sha256).digest())
    request('GET', '/products', token=expired.decode(), expected=401, error='TOKEN_EXPIRED')
    print('All negative examples passed.')


if __name__ == '__main__':
    main()

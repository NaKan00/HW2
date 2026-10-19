#!/usr/bin/env python3
"""Repeatable defense walkthrough using only Python's standard library."""
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path

BASE = os.environ.get('API_URL', 'http://localhost:8080').rstrip('/')


def request(method, path, body=None, token=None, expected=200, error=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(BASE + path, data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
    try:
        response = urllib.request.urlopen(req, timeout=15)
    except urllib.error.HTTPError as exc:
        response = exc
    with response:
        raw = response.read()
        result = json.loads(raw) if raw else {}
        assert response.status == expected, (method, path, response.status, result)
        assert response.headers.get('X-Request-Id'), 'Missing request id'
        if error:
            assert result['error_code'] == error, result
        print(f'{method} {path}: {response.status}' + (f' {error}' if error else ''))
        return result


def account(role):
    email = role.lower() + '-' + uuid.uuid4().hex + '@example.com'
    password = 'DemoPassword123!'
    user = request('POST', '/auth/register', {'email': email, 'password': password, 'role': role}, expected=201)
    tokens = request('POST', '/auth/login', {'email': email, 'password': password})
    return user, tokens['access_token']


def product_body(stock=12, price=25, status='ACTIVE'):
    return {'name': 'Notebook', 'description': 'Homework demonstration', 'price': price, 'stock': stock, 'category': 'Stationery', 'status': status}


def promo_body(code, minimum=10):
    now = datetime.now(timezone.utc)
    return {'code': code, 'discount_type': 'PERCENTAGE', 'discount_value': 10, 'min_order_amount': minimum,
            'max_uses': 10, 'valid_from': (now-timedelta(days=1)).isoformat(), 'valid_until': (now+timedelta(days=7)).isoformat(), 'active': True}


def items(product, quantity):
    return {'items': [{'product_id': product, 'quantity': quantity}]}


def inspect_database(sql):
    subprocess.run(
        ['docker', 'compose', 'exec', '-T', 'postgres', 'psql', '-U', 'marketplace',
         '-d', 'marketplace', '-v', 'ON_ERROR_STOP=1'],
        input=sql, text=True, check=True, cwd=Path(__file__).resolve().parent.parent,
    )


def main():
    _, buyer = account('USER')
    _, seller = account('SELLER')
    product = request('POST', '/products', product_body(), seller, 201)['id']
    request('GET', '/products?page=0&size=20&category=Stationery', token=buyer)
    request('GET', '/products/' + product, token=buyer)
    request('PUT', '/products/' + product, product_body(price=30), seller)
    code = 'DEMO_' + uuid.uuid4().hex[:8].upper()
    request('POST', '/promo-codes', promo_body(code), seller, 201)
    body = items(product, 2); body['promo_code'] = code
    order = request('POST', '/orders', body, buyer, 201)
    assert Decimal(str(order['total_amount'])) == Decimal('54.00')
    if '--skip-sql' not in sys.argv:
        inspect_database('''
SELECT id, email, role, created_at, updated_at FROM users ORDER BY created_at;
SELECT id, name, price, stock, category, status, seller_id, created_at, updated_at FROM products ORDER BY created_at;
SELECT * FROM orders ORDER BY created_at;
SELECT * FROM order_items ORDER BY order_id;
SELECT * FROM promo_codes ORDER BY code;
SELECT * FROM user_operations ORDER BY created_at;
''')
    request('PUT', '/orders/' + order['id'], items(product, 3), buyer)
    request('POST', '/orders/' + order['id'] + '/cancel', token=buyer)
    assert request('GET', '/products/' + product, token=buyer)['stock'] == 12
    request('DELETE', '/products/' + product, token=seller, expected=204)
    assert request('GET', '/products/' + product, token=buyer)['status'] == 'ARCHIVED'
    if '--skip-sql' not in sys.argv:
        inspect_database(f"SELECT id, status, stock FROM products WHERE id = '{product}';")
    print('Walkthrough passed: stock restored; archived product retained.')


if __name__ == '__main__':
    main()

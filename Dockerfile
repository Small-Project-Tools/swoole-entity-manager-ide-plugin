FROM php:8.4-cli-bookworm
COPY --from=composer:2 /usr/bin/composer /usr/local/bin/composer
RUN apt-get update && apt-get install -y --no-install-recommends \
    git unzip libonig-dev libxml2-dev \
    && docker-php-ext-install mbstring dom xml \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY . /app
RUN composer install --prefer-dist --no-interaction --no-progress
CMD ["php", "bin/self-test"]

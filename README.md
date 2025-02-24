The code in this repository periodically scrapes [Lemon Gym occupancy page][] and pushes the data to [Grafana Cloud][] via [OTLP][].

Scraped data can be accessed on the [publicly accessible dashboard here][].

[lemon gym occupancy page]: https://www.lemongym.lt/klubu-uzimtumas/
[Grafana Cloud]: https://grafana.com/products/cloud/
[OTLP]: https://grafana.com/docs/grafana-cloud/send-data/otlp/
[publicly accessible dashboard here]: https://dvim.grafana.net/public-dashboards/f86b6ec8bfea4dee8d3149b9857a5b1c?from=now-1h&to=now&timezone=browser

## Notice to Lemon Gym sys admins

Please do not block this. :) If you think that scraping is done too frequently, please let me know and I will adjust the frequency. This is done not for any malicious purposes, but for actually getting myself (and maybe others) to the gym more often.

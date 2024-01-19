# Rinha de Backend: 2024/Q1 - Crébito

POC para a segunda edição da Rinha de Backend feita em Clojure com as libs compojure e next.jdbc.

## Pré Requisitos

Necessário [Leiningen][] 2.0.0 ou superior instaldo.

[leiningen]: https://github.com/technomancy/leiningen

## Para Executar

Para subir o servidor, execute o seguinte comando:

    lein ring server-headless

Se quiser alterar a porta, configure a variável de ambiente `PORT` ou adicione `:port <PORTA>` na chave `:ring` no arquivo [project.clj](./project.clj).


## Conteinerização

O diretório [containerization](./containerization/) possui diversos exemplos para conteinerização da API. Talvez você precise fazer ajustes para que funcionem.


## Submissão

O diretório [participacao](./participacao) contem os artefatos usados para a submissão da Rinha de Backend.


## License

MIT - Copyright © 2024 - Poc Rinha de Backend Segunda Edição em Clojure

# language: pt
Funcionalidade: Ciclo de vida da Ordem de Serviço
  Como oficina, preciso conduzir uma OS do recebimento à entrega,
  passando pelo diagnóstico, orçamento, aprovação, execução e pagamento,
  com a possibilidade de o cliente recusar o orçamento.

  Contexto:
    Dado que estou autenticado como atendente

  Cenário: Fluxo feliz — da abertura até a entrega
    Quando eu crio uma OS para o cliente "52998224725" e veículo "ABC1234" com o serviço "TROCA_OLEO"
    Então a OS é criada com status "RECEBIDA"
    Quando a OS avança para "EM_DIAGNOSTICO"
    E a OS avança para "AGUARDANDO_APROVACAO"
    E a OS avança para "APROVADO"
    E a OS avança para "EM_EXECUCAO"
    E o mecânico executa todos os serviços da OS
    Então a OS tem status "FINALIZADA"
    Quando a OS avança para "PAGAMENTO_PENDENTE"
    E a OS avança para "PAGO"
    E a OS avança para "ENTREGUE"
    Então a OS tem status "ENTREGUE"

  Cenário: Recusa do orçamento pelo cliente
    Quando eu crio uma OS para o cliente "11144477735" e veículo "XYZ9876" com o serviço "ALINHAMENTO"
    E a OS avança para "EM_DIAGNOSTICO"
    E a OS avança para "AGUARDANDO_APROVACAO"
    E a OS avança para "RECUSADA"
    Então a OS tem status "RECUSADA"

  Cenário: Transição inválida é rejeitada
    Quando eu crio uma OS para o cliente "52998224725" e veículo "AAA1111" com o serviço "REVISAO"
    E eu tento avançar a OS diretamente para "ENTREGUE"
    Então a transição é rejeitada com erro de regra de negócio

  Cenário: OS não é finalizada com serviço pendente
    Quando eu crio uma OS para o cliente "52998224725" e veículo "BBB2222" com o serviço "TROCA_OLEO"
    E a OS avança para "EM_DIAGNOSTICO"
    E a OS avança para "AGUARDANDO_APROVACAO"
    E a OS avança para "APROVADO"
    E a OS avança para "EM_EXECUCAO"
    E eu tento avançar a OS diretamente para "FINALIZADA"
    Então a transição é recusada por conflito
    E a OS tem status "EM_EXECUCAO"

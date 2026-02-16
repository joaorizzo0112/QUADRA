# Quadra - Classic Block Puzzle

Um clone moderno e responsivo do clássico Tetris, desenvolvido inteiramente em **Java puro** (Swing/AWT), sem o uso de motores de jogos ou bibliotecas externas. 

Este projeto foi construído para aplicar conceitos sólidos de Programação Orientada a Objetos (POO), manipulação de interfaces gráficas nativas, renderização dinâmica e laços de repetição para jogos (Game Loop).

![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)
![Status](https://img.shields.io/badge/Status-Concluído-brightgreen?style=for-the-badge)

> **Nota:** <img width="959" height="539" alt="image" src="https://github.com/user-attachments/assets/7e28fd17-5f15-41b6-b1f5-29de886ab7db" />


## Funcionalidades e Mecânicas

O jogo implementa não apenas a lógica básica de colisão e pontuação, mas também recursos avançados de responsividade e retenção do jogador:

* **Escalonamento Dinâmico (Aspect Ratio Scaling):** A tela do jogo se adapta automaticamente a qualquer resolução ou tamanho de janela mantendo a proporção original (efeito Letterbox), garantindo gráficos consistentes sem distorção.
* **Mecânicas Modernas:** Sistema de Hold (guardar peça), Hard Drop (queda instantânea) e Ghost Piece (mira fantasma para prever a queda).
* **Feedback Visual:** Sistema de partículas customizado para explosões ao limpar linhas, animações de menu e paleta de cores baseada em matizes dinâmicos.
* **Áudio Procedural:** Trilha sonora e efeitos sonoros gerados em tempo real utilizando a API nativa javax.sound.midi, dispensando arquivos de áudio externos.
* **Persistência de Dados:** Sistema de High Score (recorde) salvo automaticamente em um arquivo de texto local.

## Controles

* **Setas Esquerda / Direita:** Mover peça lateralmente
* **Seta Cima:** Girar peça
* **Seta Baixo:** Descer rápido (Soft Drop)
* **Espaço:** Queda instantânea (Hard Drop)
* **C :** Armazenar ou alternar a peça em reserva estratégica (Hold)
* **P :** Pausar ou retomar o estado de execução da partida

## Tecnologias e Arquitetura

Este projeto serviu como um laboratório prático para solidificar fundamentos da linguagem Java:

* **Java Swing & AWT:** Utilizados para renderização gráfica (Graphics2D, AffineTransform), gerenciamento de janelas (JFrame, JPanel) e captura de eventos do teclado (KeyAdapter).
* **Game Loop (Timer):** Uso de javax.swing.Timer para gerenciar a taxa de atualização (FPS) e o ritmo progressivo de queda das peças.
* **Lógica de Matrizes (Arrays 2D):** Todo o gerenciamento do tabuleiro, detecção de colisões e a rotação matemática das peças baseiam-se em matrizes bidimensionais.
* **I/O (Input/Output):** Uso de FileWriter e BufferedReader para leitura e gravação segura do arquivo de pontuação.

## Como Executar Localmente

Certifique-se de ter o [Java JDK](https://www.oracle.com/java/technologies/downloads/) (versão 8 ou superior) instalado em sua máquina.

1. Clone este repositório:
   ```bash
   git clone [https://github.com/SEU-USUARIO/Quadra.git](https://github.com/SEU-USUARIO/Quadra.git)



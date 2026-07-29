package com.papyrusreader.app;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * Data Access Object (DAO) para a entidade PdfContent.
 * Contém os métodos que o Room utiliza para gerar as queries SQL em tempo de compilação.
 */
@Dao
public interface PdfContentDao {

    /**
     * Retorna todos os livros salvos na biblioteca.
     * A ordenação é feita do acesso mais recente para o mais antigo (DESC),
     * garantindo o comportamento LIFO (Last-In, First-Out) na tela inicial.
     *
     * @return Lista completa de livros ordenada por tempo de acesso.
     */
    @Query("SELECT * FROM pdfcontent ORDER BY last_time_opened DESC")
    List<PdfContent> getAll();

    /**
     * Busca um livro específico pelo seu título.
     * Utilizado para verificar duplicidades na importação e carregar os dados na tela de leitura.
     *
     * @param title O título do PDF desejado.
     * @return A entidade PdfContent correspondente, ou null se não for encontrada.
     */
    @Query("SELECT * FROM pdfcontent WHERE title = :title")
    PdfContent getByTitle(String title);

    /**
     * Insere um novo livro no banco de dados.
     *
     * @param pdfContent Objeto preenchido com o texto, título e caminhos da capa.
     */
    @Insert
    void insert(PdfContent pdfContent);

    /**
     * Atualiza os dados de um livro já existente.
     * É chamado constantemente para salvar a posição de rolagem e atualizar a data de último acesso.
     *
     * @param pdfContent O objeto modificado que substituirá o antigo no banco.
     */
    @Update
    void update(PdfContent pdfContent);

    /**
     * Deleta permanentemente um livro do banco de dados.
     * Utilizado ao final do gesto de "Swipe to Delete" quando a opção "Desfazer" não é acionada.
     *
     * @param pdfContent O objeto que será removido.
     */
    @Delete
    void delete(PdfContent pdfContent);
}
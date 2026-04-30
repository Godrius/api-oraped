package br.com.oraped.integration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.enums.TipoDiretorioFtp;

@Component
public class HostingerClient {

    @Value("${ftp.server}")
    private String ftpServer;

    @Value("${ftp.port}")
    private int ftpPort;

    @Value("${ftp.user}")
    private String ftpUser;

    @Value("${ftp.password}")
    private String ftpPassword;

    @Value("${ftp.upload-dir}")
    private String ftpUploadDir;

    @Value("${ftp.public-base-url}")
    private String ftpPublicBaseUrl;

    /**
     * Faz upload da foto principal do produto e retorna a URL pública final.
     *
     * Regra de armazenamento:
     * - diretório remoto: /public_html/oraped/clientes/{idEstabelecimento}/produtos
     * - nome do arquivo: {idProduto}.{ext}
     */
    public String uploadFotoProduto(
        Long idEstabelecimento,
        Long idProduto,
        byte[] conteudoArquivo,
        String mimeType
    ) {

        validarUploadFotoProduto(idEstabelecimento, idProduto, conteudoArquivo, mimeType);

        String extensao = resolverExtensaoPorMimeType(mimeType);
        String nomeArquivo = idProduto + "." + extensao;

        String caminhoRelativoDiretorio = montarCaminhoRelativo(
            TipoDiretorioFtp.PRODUTO_FOTO,
            idEstabelecimento,
            null
        );

        String caminhoRemotoDiretorio = normalizarSemBarraFinal(ftpUploadDir) + "/" + caminhoRelativoDiretorio;
        String caminhoRemotoArquivo = caminhoRemotoDiretorio + "/" + nomeArquivo;

        FTPClient ftpClient = new FTPClient();

        try (InputStream inputStream = new ByteArrayInputStream(conteudoArquivo)) {

            conectar(ftpClient);

            garantirDiretorioRecursivo(ftpClient, caminhoRemotoDiretorio);

            // Remove possíveis versões antigas com outras extensões antes de subir a nova.
            removerArquivosFotoProdutoExistentes(ftpClient, idEstabelecimento, idProduto);

            boolean done = ftpClient.storeFile(caminhoRemotoArquivo, inputStream);
            ftpClient.logout();

            if (!done) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Falha no upload da foto do produto via FTP"
                );
            }

            return montarUrlPublica(
                TipoDiretorioFtp.PRODUTO_FOTO,
                idEstabelecimento,
                null,
                nomeArquivo
            );

        } catch (IOException ex) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Erro ao conectar ao FTP para enviar foto do produto",
                ex
            );
        } finally {
            desconectarSilenciosamente(ftpClient);
        }
    }

    /**
     * Remove o arquivo correspondente à URL atual do produto, se existir.
     * Esse método é útil quando a foto for apagada ou substituída.
     */
    public boolean deleteByUrl(String urlArquivo) {

        if (!StringUtils.hasText(urlArquivo)) {
            return false;
        }

        String basePublica = normalizarSemBarraFinal(ftpPublicBaseUrl);
        String urlLimpa = urlArquivo.trim();

        if (!urlLimpa.startsWith(basePublica + "/")) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "A URL informada não pertence ao diretório público configurado do FTP"
            );
        }

        String caminhoRelativo = urlLimpa.substring((basePublica + "/").length());
        String caminhoRemoto = normalizarSemBarraFinal(ftpUploadDir) + "/" + caminhoRelativo;

        FTPClient ftpClient = new FTPClient();

        try {
            conectar(ftpClient);

            boolean deleted = ftpClient.deleteFile(caminhoRemoto);
            ftpClient.logout();

            return deleted;

        } catch (IOException ex) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Erro ao conectar ao FTP para remover arquivo",
                ex
            );
        } finally {
            desconectarSilenciosamente(ftpClient);
        }
    }

    public String montarUrlPublica(
        TipoDiretorioFtp tipoDiretorio,
        Long idEstabelecimento,
        Long idOpcional,
        String nomeArquivo
    ) {

        String basePublica = normalizarSemBarraFinal(ftpPublicBaseUrl);
        String caminhoRelativo = montarCaminhoRelativo(tipoDiretorio, idEstabelecimento, idOpcional);

        return basePublica + "/" + caminhoRelativo + "/" + nomeArquivo;
    }

    private void validarUploadFotoProduto(
        Long idEstabelecimento,
        Long idProduto,
        byte[] conteudoArquivo,
        String mimeType
    ) {

        if (idEstabelecimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
        }

        if (idProduto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idProduto é obrigatório");
        }

        if (conteudoArquivo == null || conteudoArquivo.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conteudoArquivo é obrigatório");
        }

        if (!StringUtils.hasText(mimeType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mimeType é obrigatório");
        }
    }

    private void conectar(FTPClient ftpClient) throws IOException {

        ftpClient.connect(ftpServer, ftpPort);
        ftpClient.login(ftpUser, ftpPassword);
        ftpClient.enterLocalPassiveMode();
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
    }

    /**
     * Garante a existência do diretório completo, criando segmento por segmento.
     */
    private void garantirDiretorioRecursivo(FTPClient ftpClient, String caminhoCompleto) throws IOException {

        String[] partes = caminhoCompleto.split("/");

        StringBuilder atual = new StringBuilder();

        for (String parte : partes) {

            if (!StringUtils.hasText(parte)) {
                continue;
            }

            atual.append("/").append(parte);
            String diretorioAtual = atual.toString();

            if (!ftpClient.changeWorkingDirectory(diretorioAtual)) {
                boolean created = ftpClient.makeDirectory(diretorioAtual);
                if (!created && !ftpClient.changeWorkingDirectory(diretorioAtual)) {
                    throw new IOException("Não foi possível criar/acessar o diretório FTP: " + diretorioAtual);
                }
            }
        }
    }

    /**
     * Antes de subir a nova foto, remove possíveis arquivos antigos do mesmo produto
     * com extensões conhecidas para evitar sobras desnecessárias no servidor.
     */
    private void removerArquivosFotoProdutoExistentes(
        FTPClient ftpClient,
        Long idEstabelecimento,
        Long idProduto
    ) throws IOException {

        String caminhoRelativoDiretorio = montarCaminhoRelativo(
            TipoDiretorioFtp.PRODUTO_FOTO,
            idEstabelecimento,
            null
        );

        String diretorioRemoto = normalizarSemBarraFinal(ftpUploadDir) + "/" + caminhoRelativoDiretorio;

        ftpClient.deleteFile(diretorioRemoto + "/" + idProduto + ".jpg");
        ftpClient.deleteFile(diretorioRemoto + "/" + idProduto + ".jpeg");
        ftpClient.deleteFile(diretorioRemoto + "/" + idProduto + ".png");
        ftpClient.deleteFile(diretorioRemoto + "/" + idProduto + ".webp");
    }

    private String montarCaminhoRelativo(
        TipoDiretorioFtp tipoDiretorio,
        Long idEstabelecimento,
        Long idOpcional
    ) {

        if (tipoDiretorio == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tipoDiretorio é obrigatório");
        }

        if (tipoDiretorio == TipoDiretorioFtp.PRODUTO_FOTO) {

            if (idEstabelecimento == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
            }

            return "clientes/" + idEstabelecimento + "/produtos";
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de diretório FTP não suportado");
    }

    private String resolverExtensaoPorMimeType(String mimeType) {

        String mime = mimeType.trim().toLowerCase();

        switch (mime) {
            case "image/jpeg":
            case "image/jpg":
                return "jpg";

            case "image/png":
                return "png";

            case "image/webp":
                return "webp";

            default:
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Formato de imagem não suportado: " + mimeType
                );
        }
    }

    private String normalizarSemBarraFinal(String value) {

        String v = value == null ? null : value.trim();

        if (!StringUtils.hasText(v)) {
            return v;
        }

        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }

        return v;
    }

    private void desconectarSilenciosamente(FTPClient ftpClient) {

        try {
            if (ftpClient != null && ftpClient.isConnected()) {
                ftpClient.disconnect();
            }
        } catch (IOException ignored) {
        }
    }
}
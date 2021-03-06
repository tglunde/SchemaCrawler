/*
========================================================================
SchemaCrawler
http://www.schemacrawler.com
Copyright (c) 2000-2019, Sualeh Fatehi <sualeh@hotmail.com>.
All rights reserved.
------------------------------------------------------------------------

SchemaCrawler is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

SchemaCrawler and the accompanying materials are made available under
the terms of the Eclipse Public License v1.0, GNU General Public License
v3 or GNU Lesser General Public License v3.

You may elect to redistribute this code under any of these licenses.

The Eclipse Public License is available at:
http://www.eclipse.org/legal/epl-v10.html

The GNU General Public License v3 and the GNU Lesser General Public
License v3 are available at:
http://www.gnu.org/licenses/

========================================================================
*/

package schemacrawler.tools.commandline.command;


import static java.util.Objects.requireNonNull;

import java.sql.Connection;
import java.util.logging.Level;

import picocli.CommandLine;
import schemacrawler.schema.Catalog;
import schemacrawler.schemacrawler.Config;
import schemacrawler.schemacrawler.InfoLevel;
import schemacrawler.schemacrawler.SchemaCrawlerOptions;
import schemacrawler.schemacrawler.SchemaRetrievalOptions;
import schemacrawler.tools.catalogloader.CatalogLoader;
import schemacrawler.tools.catalogloader.CatalogLoaderRegistry;
import schemacrawler.tools.commandline.state.SchemaCrawlerShellState;
import sf.util.SchemaCrawlerLogger;
import sf.util.StringFormat;

@CommandLine.Command(name = "load", description = "Load database metadata")
public class LoadCommand
  implements Runnable
{

  private static final SchemaCrawlerLogger LOGGER = SchemaCrawlerLogger
    .getLogger(LoadCommand.class.getName());

  private final SchemaCrawlerShellState state;

  @CommandLine.Option(names = {
    "-i",
    "--info-level" }, required = true, description = "Determine the amount of database metadata retrieved")
  private InfoLevel infoLevel;

  @CommandLine.Spec
  private CommandLine.Model.CommandSpec spec;

  public LoadCommand(final SchemaCrawlerShellState state)
  {
    this.state = requireNonNull(state, "No state provided");
  }

  public InfoLevel getInfoLevel()
  {
    return infoLevel;
  }

  public void run()
  {
    if (!state.isConnected())
    {
      throw new CommandLine.ExecutionException(spec.commandLine(),
                                               "Not connected to the database");
    }

    if (infoLevel != null)
    {
      state.getSchemaCrawlerOptionsBuilder()
        .withSchemaInfoLevel(infoLevel.toSchemaInfoLevel());
    }

    try (final Connection connection = state.getDataSource().getConnection())
    {
      LOGGER.log(Level.INFO, new StringFormat("infoLevel=%s", infoLevel));

      final Config additionalConfiguration = state.getAdditionalConfiguration();
      final SchemaRetrievalOptions schemaRetrievalOptions = state
        .getSchemaRetrievalOptionsBuilder().toOptions();
      final SchemaCrawlerOptions schemaCrawlerOptions = state
        .getSchemaCrawlerOptionsBuilder().toOptions();

      final CatalogLoaderRegistry catalogLoaderRegistry = new CatalogLoaderRegistry();
      final CatalogLoader catalogLoader = catalogLoaderRegistry
        .lookupCatalogLoader(schemaRetrievalOptions.getDatabaseServerType()
                               .getDatabaseSystemIdentifier());
      LOGGER.log(Level.CONFIG,
                 new StringFormat("Catalog loader: %s", getClass().getName()));

      catalogLoader.setAdditionalConfiguration(additionalConfiguration);
      catalogLoader.setConnection(connection);
      catalogLoader.setSchemaRetrievalOptions(schemaRetrievalOptions);
      catalogLoader.setSchemaCrawlerOptions(schemaCrawlerOptions);

      final Catalog catalog = catalogLoader.loadCatalog();
      requireNonNull(catalog, "Catalog could not be retrieved");

      state.setCatalog(catalog);
      LOGGER.log(Level.INFO, "Loaded catalog");

    }
    catch (final Exception e)
    {
      throw new CommandLine.ExecutionException(spec.commandLine(),
                                               "Cannot load catalog",
                                               e);
    }
  }

}
